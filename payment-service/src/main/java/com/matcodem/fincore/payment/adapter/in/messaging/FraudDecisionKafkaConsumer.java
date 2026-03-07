package com.matcodem.fincore.payment.adapter.in.messaging;


import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.matcodem.fincore.payment.infrastructure.idempotency.IdempotencyGuard;
import com.matcodem.fincore.payment.domain.port.in.ProcessPaymentUseCase;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Listens to fraud.case.* events and drives payment lifecycle accordingly.
 * <p>
 * IDEMPOTENCY:
 * Every handler calls idempotencyGuard.tryProcess(eventId, ...) FIRST.
 * If Kafka redelivers the same event (broker retry, consumer restart):
 * → tryProcess returns false → we ACK immediately and return.
 * → No double-debit, no double-credit, no double-reject.
 * <p>
 * eventId extracted from payload; fallback to topic+partition+offset.
 * <p>
 * FLOW:
 * fraud.case.approved  → processPayment()           → debit + credit → COMPLETED
 * fraud.case.blocked   → rejectForFraud()            → REJECTED_FRAUD, no money moves
 * fraud.case.escalated → no-op                       → payment stays PENDING
 * fraud.confirmed      → initiateReversalIfNeeded()  → reverse if COMPLETED
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FraudDecisionKafkaConsumer {

	static final String GROUP_ID = "payment-service-fraud-consumer";

	private final ProcessPaymentUseCase processPaymentUseCase;
	private final IdempotencyGuard idempotencyGuard;
	private final ObjectMapper objectMapper;
	private final MeterRegistry meterRegistry;

	@KafkaListener(
			topics = "fincore.fraud.case-approved",
			groupId = GROUP_ID,
			containerFactory = "paymentKafkaListenerContainerFactory"
	)
	public void onFraudApproved(ConsumerRecord<String, String> record, Acknowledgment ack) {
		String paymentId = record.key();
		try {
			Map<String, Object> event = deserialize(record.value());
			String eventId = extractEventId(event, record);

			if (!idempotencyGuard.tryProcess(eventId, "fraud.case.approved", GROUP_ID)) {
				ack.acknowledge();
				return;
			}

			log.info("Fraud APPROVED for payment {} — proceeding", paymentId);
			processPaymentUseCase.processPayment(paymentId);
			meterRegistry.counter("payment.fraud.approved.processed").increment();
			ack.acknowledge();

		} catch (Exception ex) {
			log.error("Failed to process approved payment {}: {}", paymentId, ex.getMessage(), ex);
			meterRegistry.counter("payment.fraud.approved.failed").increment();
			throw new RuntimeException("Payment processing failed after fraud approval: " + paymentId, ex);
		}
	}

	@KafkaListener(
			topics = "fincore.fraud.case-blocked",
			groupId = GROUP_ID,
			containerFactory = "paymentKafkaListenerContainerFactory"
	)
	public void onFraudBlocked(ConsumerRecord<String, String> record, Acknowledgment ack) {
		String paymentId = record.key();
		try {
			Map<String, Object> event = deserialize(record.value());
			String eventId = extractEventId(event, record);

			if (!idempotencyGuard.tryProcess(eventId, "fraud.case.blocked", GROUP_ID)) {
				ack.acknowledge();
				return;
			}

			String reason = (String) event.getOrDefault("reason", "Blocked by fraud detection");
			log.warn("Fraud BLOCKED payment {} — reason: {}", paymentId, reason);
			processPaymentUseCase.rejectForFraud(paymentId, reason);
			meterRegistry.counter("payment.fraud.blocked").increment();
			ack.acknowledge();

		} catch (Exception ex) {
			log.error("Failed to reject fraud-blocked payment {}: {}", paymentId, ex.getMessage(), ex);
			throw new RuntimeException("Failed to reject payment: " + paymentId, ex);
		}
	}

	@KafkaListener(
			topics = "fincore.fraud.case-escalated",
			groupId = GROUP_ID,
			containerFactory = "paymentKafkaListenerContainerFactory"
	)
	public void onFraudEscalated(ConsumerRecord<String, String> record, Acknowledgment ack) {
		String paymentId = record.key();
		try {
			Map<String, Object> event = deserialize(record.value());
			String eventId = extractEventId(event, record);

			if (!idempotencyGuard.tryProcess(eventId, "fraud.case.escalated", GROUP_ID)) {
				ack.acknowledge();
				return;
			}

			log.info("Fraud ESCALATED for payment {} — awaiting compliance review", paymentId);
			meterRegistry.counter("payment.fraud.escalated").increment();
			ack.acknowledge();

		} catch (Exception ex) {
			log.error("Escalation handler failed for payment {}: {}", paymentId, ex.getMessage(), ex);
			throw new RuntimeException("Escalation handler failed: " + paymentId, ex);
		}
	}

	@KafkaListener(
			topics = "fincore.fraud.confirmed",
			groupId = GROUP_ID,
			containerFactory = "paymentKafkaListenerContainerFactory"
	)
	public void onFraudConfirmed(ConsumerRecord<String, String> record, Acknowledgment ack) {
		String paymentId = record.key();
		try {
			Map<String, Object> event = deserialize(record.value());
			String eventId = extractEventId(event, record);

			if (!idempotencyGuard.tryProcess(eventId, "fraud.confirmed", GROUP_ID)) {
				ack.acknowledge();
				return;
			}

			log.error("FRAUD CONFIRMED for payment {} — initiating reversal if needed", paymentId);
			processPaymentUseCase.initiateReversalIfNeeded(paymentId,
					(String) event.getOrDefault("notes", "Confirmed fraud"));
			meterRegistry.counter("payment.fraud.confirmed.reversal").increment();
			ack.acknowledge();

		} catch (Exception ex) {
			log.error("Fraud confirmed handler failed for payment {}: {}", paymentId, ex.getMessage(), ex);
			throw new RuntimeException("Fraud confirmed handler failed: " + paymentId, ex);
		}
	}

	private String extractEventId(Map<String, Object> event, ConsumerRecord<?, ?> record) {
		Object id = event.get("eventId");
		if (id != null && !id.toString().isBlank()) return id.toString();
		// Deterministic fallback: unique within topic+partition
		return "%s-%d-%d".formatted(record.topic(), record.partition(), record.offset());
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> deserialize(String json) throws Exception {
		return objectMapper.readValue(json, Map.class);
	}
}