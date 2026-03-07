package com.matcodem.fincore.payment.adapter.in.messaging;

import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.matcodem.fincore.payment.domain.port.in.ProcessPaymentUseCase;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Listens to FX conversion events and continues payment processing.
 * <p>
 * FLOW for FX_CONVERSION payments:
 * <p>
 * 1. Payment Service initiates payment (PENDING)
 * 2. Publishes payment.initiated → Fraud Service analyses
 * 3. fraud.case.approved → Payment Service triggers FX conversion via REST
 * (synchronous — we need the converted amount before we can debit/credit)
 * <p>
 * NOTE: FX conversion is called synchronously within processPayment(),
 * not via Kafka. These consumers handle edge cases:
 * <p>
 * fx.conversion.executed — confirmation that async conversion went through
 * (rare: used only for FX batch processing, not normal payment flow)
 * <p>
 * fx.conversion.failed — FX Service failed to convert (rate unavailable, provider down)
 * → Payment Service marks payment FAILED
 * → Publishes payment.failed so customer is notified
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FxConversionResultKafkaConsumer {

	private final ProcessPaymentUseCase processPaymentUseCase;
	private final ObjectMapper objectMapper;
	private final MeterRegistry meterRegistry;

	@KafkaListener(
			topics = "fincore.fx.conversion-failed",
			groupId = "payment-service-fx-consumer",
			containerFactory = "paymentKafkaListenerContainerFactory"
	)
	public void onFxConversionFailed(ConsumerRecord<String, String> record, Acknowledgment ack) {
		String paymentId = record.key();

		try {
			Map<String, Object> event = deserialize(record.value());
			String reason = (String) event.getOrDefault("reason", "FX conversion failed");
			String pair = (String) event.getOrDefault("pair", "unknown");

			log.error("FX conversion FAILED for payment {} — pair: {}, reason: {}",
					paymentId, pair, reason);

			processPaymentUseCase.failPayment(paymentId,
					"FX conversion failed (%s): %s".formatted(pair, reason));

			meterRegistry.counter("payment.fx.conversion.failed",
					"pair", pair).increment();
			ack.acknowledge();

		} catch (Exception ex) {
			log.error("Failed to handle FX conversion failure for payment {}: {}",
					paymentId, ex.getMessage(), ex);
			throw new RuntimeException("FX failure handler failed: " + paymentId, ex);
		}
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> deserialize(String json) throws Exception {
		return objectMapper.readValue(json, Map.class);
	}
}
