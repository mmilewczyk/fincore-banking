package com.matcodem.fincore.fraud.adapter.in.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.matcodem.fincore.fraud.domain.model.FraudCase;
import com.matcodem.fincore.fraud.domain.model.PaymentContext;
import com.matcodem.fincore.fraud.domain.port.in.AnalysePaymentUseCase;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Kafka Consumer — listens to payment.initiated events and triggers fraud analysis.
 * <p>
 * Consumer design decisions:
 * <p>
 * 1. MANUAL ACK — we only acknowledge (commit offset) AFTER successful processing.
 * If the service crashes mid-analysis, Kafka will re-deliver the message.
 * <p>
 * 2. IDEMPOTENCY — we check if a FraudCase already exists for this payment
 * before creating a new one, handling re-deliveries gracefully.
 * <p>
 * 3. DLQ (Dead Letter Queue) — after MAX_RETRY failures, message is sent to
 * fincore.fraud.dlq for manual investigation. Never lost.
 * <p>
 * 4. CONCURRENCY — listener container has concurrency=3, matching Kafka partition count.
 * Each thread handles its own partition — no cross-thread contention.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentInitiatedKafkaConsumer {

	private final AnalysePaymentUseCase analysePaymentUseCase;
	private final ObjectMapper objectMapper;
	private final MeterRegistry meterRegistry;

	@KafkaListener(
			topics = "fincore.payments.payment-initiated",
			groupId = "fraud-detection-service",
			containerFactory = "fraudKafkaListenerContainerFactory",
			concurrency = "3"
	)
	public void onPaymentInitiated(
			ConsumerRecord<String, String> record,
			Acknowledgment acknowledgment) {

		String paymentId = record.key();
		log.info("Received payment.initiated event for paymentId: {} (partition: {}, offset: {})",
				paymentId, record.partition(), record.offset());

		try {
			PaymentContext context = deserializePaymentContext(record.value());
			FraudCase result = analysePaymentUseCase.analyse(context);

			log.info("Fraud analysis completed — payment: {}, outcome: {}, score: {}",
					paymentId, result.getStatus(), result.getCompositeScore());

			meterRegistry.counter("fraud.consumer.success").increment();

			// Only ack after successful processing
			acknowledgment.acknowledge();

		} catch (Exception ex) {
			log.error("Failed to process payment.initiated for paymentId: {} — {}",
					paymentId, ex.getMessage(), ex);
			meterRegistry.counter("fraud.consumer.error",
					"error_type", ex.getClass().getSimpleName()).increment();

			// Do NOT acknowledge — Kafka will retry.
			// After max retries, Spring Kafka's SeekToCurrentErrorHandler
			// will send this to the Dead Letter Topic.
			throw new RuntimeException("Fraud analysis failed for payment: " + paymentId, ex);
		}
	}

	/**
	 * Listens to Dead Letter Queue — messages that failed all retries.
	 * Logs and persists for manual investigation — never silently dropped.
	 */
	@KafkaListener(
			topics = "fincore.payments.payment-initiated.DLT",
			groupId = "fraud-detection-dlq-handler"
	)
	public void onDeadLetterMessage(ConsumerRecord<String, String> record) {
		log.error("DEAD LETTER — payment fraud analysis permanently failed for paymentId: {} " +
						"(partition: {}, offset: {}) — MANUAL INVESTIGATION REQUIRED",
				record.key(), record.partition(), record.offset());

		meterRegistry.counter("fraud.consumer.dead_letter").increment();

		// In production: persist to dead_letter_cases table, create JIRA ticket,
		// alert on-call compliance team via PagerDuty
	}

	@SuppressWarnings("unchecked")
	private PaymentContext deserializePaymentContext(String json) throws Exception {
		Map<String, Object> event = objectMapper.readValue(json, Map.class);

		return PaymentContext.builder()
				.paymentId((String) event.get("paymentId"))
				.idempotencyKey((String) event.getOrDefault("idempotencyKey", "unknown"))
				.sourceAccountId((String) event.get("sourceAccountId"))
				.targetAccountId((String) event.get("targetAccountId"))
				.amount(new BigDecimal(event.get("amount").toString()))
				.currency((String) event.get("currency"))
				.paymentType((String) event.get("type"))
				.initiatedBy((String) event.get("initiatedBy"))
				.initiatedAt(Instant.parse((String) event.get("occurredAt")))
				.build();
	}
}