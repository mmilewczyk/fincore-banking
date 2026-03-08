package com.matcodem.fincore.payment.adapter.in.messaging;

import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.matcodem.fincore.payment.domain.port.in.ProcessPaymentUseCase;
import com.matcodem.fincore.payment.infrastructure.idempotency.IdempotencyGuard;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Handles FX conversion failure events from FX Service.
 * <p>
 * NORMAL FLOW:
 * FX conversion is called SYNCHRONOUSLY within processPayment() - we need
 * the converted amount immediately before debiting/crediting accounts.
 * FxServiceWebClient.convert() blocks until the FX Service responds.
 * <p>
 * THIS CONSUMER handles the async edge case:
 * fincore.fx.conversion-failed - FX Service published a failure event
 * (provider timeout, rate unavailable) for a conversion that was initiated
 * outside the normal synchronous flow (e.g. retry jobs, batch processing).
 * -> Payment Service marks the payment FAILED.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FxConversionResultKafkaConsumer {

	static final String GROUP_ID = "payment-service-fx-consumer";

	private final ProcessPaymentUseCase processPaymentUseCase;
	private final IdempotencyGuard idempotencyGuard;
	private final ObjectMapper objectMapper;
	private final MeterRegistry meterRegistry;

	@KafkaListener(
			topics = "fincore.fx.conversion-failed",
			groupId = GROUP_ID,
			containerFactory = "paymentKafkaListenerContainerFactory"
	)
	public void onFxConversionFailed(ConsumerRecord<String, String> record, Acknowledgment ack) {
		String paymentId = record.key();

		try {
			Map<String, Object> event = deserialize(record.value());
			String eventId = extractEventId(event, record);

			if (!idempotencyGuard.tryProcess(eventId, "fx.conversion.failed", GROUP_ID)) {
				ack.acknowledge();
				return;
			}

			String reason = (String) event.getOrDefault("reason", "FX conversion failed");
			String pair = (String) event.getOrDefault("pair", "unknown");

			log.error("FX conversion FAILED for payment {} - pair: {}, reason: {}",
					paymentId, pair, reason);

			processPaymentUseCase.failPayment(paymentId,
					"FX conversion failed (%s): %s".formatted(pair, reason));

			meterRegistry.counter("payment.fx.conversion.failed", "pair", pair).increment();
			ack.acknowledge();

		} catch (Exception ex) {
			log.error("Failed to handle FX conversion failure for payment {}: {}",
					paymentId, ex.getMessage(), ex);
			throw new RuntimeException("FX failure handler failed: " + paymentId, ex);
		}
	}

	private String extractEventId(Map<String, Object> event, ConsumerRecord<?, ?> record) {
		Object id = event.get("eventId");
		if (id != null && !id.toString().isBlank()) return id.toString();
		return "%s-%d-%d".formatted(record.topic(), record.partition(), record.offset());
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> deserialize(String json) throws Exception {
		return objectMapper.readValue(json, Map.class);
	}
}