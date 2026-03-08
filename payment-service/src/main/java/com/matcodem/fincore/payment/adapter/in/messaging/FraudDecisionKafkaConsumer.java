package com.matcodem.fincore.payment.adapter.in.messaging;


import com.matcodem.fincore.fraud.avro.FraudCaseApprovedEvent;
import com.matcodem.fincore.fraud.avro.FraudCaseBlockedEvent;
import com.matcodem.fincore.fraud.avro.FraudCaseEscalatedEvent;
import com.matcodem.fincore.fraud.avro.FraudConfirmedEvent;
import com.matcodem.fincore.payment.domain.port.in.ProcessPaymentUseCase;
import com.matcodem.fincore.payment.infrastructure.idempotency.IdempotencyGuard;
import com.matcodem.fincore.payment.infrastructure.messaging.avro.AvroEventMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Listens to fraud.case.* events and drives the payment lifecycle accordingly.
 *
 * AVRO DESERIALIZATION:
 *   KafkaAvroDeserializer (configured in KafkaConsumerConfig) reads the Confluent wire format:
 *     [magic byte 0x00][schema-id 4 bytes][avro payload]
 *   It fetches the writer schema by ID from Schema Registry, applies BACKWARD compatibility
 *   conversion to the reader schema (generated class), and returns a typed SpecificRecord.
 *
 *   The @KafkaListener receives ConsumerRecord<String, Object> because the deserializer
 *   returns SpecificRecord subtypes. We cast to the expected type per topic.
 *   If the wrong type arrives on a topic, ClassCastException routes the message to DLT
 *   via DefaultErrorHandler - no data loss.
 *
 * IDEMPOTENCY:
 *   eventId is now a typed String from the Avro record - no JSON parsing, no null risk.
 *   Fallback to topic+partition+offset is retained for defense in depth.
 *
 * BACKWARD COMPATIBILITY:
 *   If Fraud Service adds a field to FraudCaseBlockedEvent (e.g. ruleScores map),
 *   old payment-service instances still work - KafkaAvroDeserializer fills new fields
 *   with their default values from the .avsc. No redeployment required.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FraudDecisionKafkaConsumer {

	static final String GROUP_ID = "payment-service-fraud-consumer";

	private final ProcessPaymentUseCase processPaymentUseCase;
	private final IdempotencyGuard idempotencyGuard;
	private final AvroEventMapper avroEventMapper;
	private final MeterRegistry meterRegistry;

	@KafkaListener(
			topics = "fincore.fraud.case-approved",
			groupId = GROUP_ID,
			containerFactory = "paymentKafkaListenerContainerFactory"
	)
	public void onFraudApproved(ConsumerRecord<String, Object> record, Acknowledgment ack) {
		FraudCaseApprovedEvent event = (FraudCaseApprovedEvent) record.value();
		String paymentId = avroEventMapper.extractPaymentId(event);
		String eventId = extractEventId(event.getEventId(), record);

		try {
			if (!idempotencyGuard.tryProcess(eventId, "fraud.case.approved", GROUP_ID)) {
				ack.acknowledge();
				return;
			}

			log.info("Fraud APPROVED: paymentId={}, fraudScore={}, rulesEvaluated={}",
					paymentId, event.getFraudScore(), event.getRulesEvaluated());

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
	public void onFraudBlocked(ConsumerRecord<String, Object> record, Acknowledgment ack) {
		FraudCaseBlockedEvent event = (FraudCaseBlockedEvent) record.value();
		String paymentId = avroEventMapper.extractPaymentId(event);
		String eventId = extractEventId(event.getEventId(), record);

		try {
			if (!idempotencyGuard.tryProcess(eventId, "fraud.case.blocked", GROUP_ID)) {
				ack.acknowledge();
				return;
			}

			String reason = avroEventMapper.extractReason(event);
			log.warn("Fraud BLOCKED: paymentId={}, score={}, rule={}, reason={}",
					paymentId, event.getFraudScore(), event.getTriggeringRule(), event.getReason());

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
	public void onFraudEscalated(ConsumerRecord<String, Object> record, Acknowledgment ack) {
		FraudCaseEscalatedEvent event = (FraudCaseEscalatedEvent) record.value();
		String paymentId = avroEventMapper.extractPaymentId(event);
		String eventId = extractEventId(event.getEventId(), record);

		try {
			if (!idempotencyGuard.tryProcess(eventId, "fraud.case.escalated", GROUP_ID)) {
				ack.acknowledge();
				return;
			}

			// Payment stays PENDING - awaiting manual compliance review
			log.info("Fraud ESCALATED: paymentId={}, score={}, reason={}",
					paymentId, event.getFraudScore(), event.getEscalationReason());

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
	public void onFraudConfirmed(ConsumerRecord<String, Object> record, Acknowledgment ack) {
		FraudConfirmedEvent event = (FraudConfirmedEvent) record.value();
		String paymentId = avroEventMapper.extractPaymentId(event);
		String eventId = extractEventId(event.getEventId(), record);

		try {
			if (!idempotencyGuard.tryProcess(eventId, "fraud.confirmed", GROUP_ID)) {
				ack.acknowledge();
				return;
			}

			log.error("FRAUD CONFIRMED: paymentId={}, confirmedBy={}, reversalRequired={}",
					paymentId, event.getConfirmedBy(), event.getReversalRequired());

			if (avroEventMapper.isReversalRequired(event)) {
				processPaymentUseCase.initiateReversalIfNeeded(paymentId,
						avroEventMapper.extractNotes(event));
			} else {
				log.info("Reversal skipped for payment {} - already handled externally", paymentId);
			}

			meterRegistry.counter("payment.fraud.confirmed.reversal").increment();
			ack.acknowledge();

		} catch (Exception ex) {
			log.error("Fraud confirmed handler failed for payment {}: {}", paymentId, ex.getMessage(), ex);
			throw new RuntimeException("Fraud confirmed handler failed: " + paymentId, ex);
		}
	}

	private String extractEventId(String avroEventId, ConsumerRecord<?, ?> record) {
		if (avroEventId != null && !avroEventId.isBlank()) return avroEventId;
		// Deterministic fallback: unique within topic+partition
		return "%s-%d-%d".formatted(record.topic(), record.partition(), record.offset());
	}
}