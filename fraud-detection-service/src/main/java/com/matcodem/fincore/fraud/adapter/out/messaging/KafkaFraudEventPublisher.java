package com.matcodem.fincore.fraud.adapter.out.messaging;

import java.util.List;
import java.util.Map;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.matcodem.fincore.fraud.domain.event.DomainEvent;
import com.matcodem.fincore.fraud.domain.event.FraudCaseApprovedEvent;
import com.matcodem.fincore.fraud.domain.event.FraudCaseBlockedEvent;
import com.matcodem.fincore.fraud.domain.event.FraudCaseEscalatedEvent;
import com.matcodem.fincore.fraud.domain.event.FraudConfirmedEvent;
import com.matcodem.fincore.fraud.domain.port.out.FraudEventPublisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Adapter — publishes fraud domain events to Kafka.
 * <p>
 * Topic routing:
 * FraudCaseApprovedEvent   → fincore.fraud.case-approved
 * FraudCaseBlockedEvent    → fincore.fraud.case-blocked     (Payment Service listens here)
 * FraudCaseEscalatedEvent  → fincore.fraud.case-escalated
 * FraudConfirmedEvent      → fincore.fraud.confirmed        (Account Service listens to freeze)
 * <p>
 * Key = paymentId — ensures events for the same payment land on the same partition,
 * preserving ordering guarantees within a payment's lifecycle.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaFraudEventPublisher implements FraudEventPublisher {

	private final KafkaTemplate<String, String> kafkaTemplate;
	private final ObjectMapper objectMapper;

	private static final String TOPIC_PREFIX = "fincore.fraud.";

	@Override
	public void publishAll(List<DomainEvent> events) {
		events.forEach(this::publish);
	}

	private void publish(DomainEvent event) {
		String topic = TOPIC_PREFIX + event.eventType().replace("fraud.", "").replace(".", "-");
		String key = resolveKey(event);

		try {
			String payload = objectMapper.writeValueAsString(toPayload(event));

			kafkaTemplate.send(topic, key, payload)
					.whenComplete((result, ex) -> {
						if (ex != null) {
							log.error("Failed to publish fraud event {} to {}: {}",
									event.eventType(), topic, ex.getMessage());
						} else {
							log.debug("Published fraud event {} → {} (offset: {})",
									event.eventType(), topic,
									result.getRecordMetadata().offset());
						}
					});
		} catch (Exception ex) {
			// Serialization failure is a programming error — fail fast
			throw new RuntimeException("Failed to serialize fraud event: " + event.eventType(), ex);
		}
	}

	/**
	 * Use paymentId as the Kafka key so all events for a payment
	 * go to the same partition and are consumed in order.
	 */
	private String resolveKey(DomainEvent event) {
		return switch (event) {
			case FraudCaseApprovedEvent e -> e.paymentId();
			case FraudCaseBlockedEvent e -> e.paymentId();
			case FraudCaseEscalatedEvent e -> e.paymentId();
			case FraudConfirmedEvent e -> e.paymentId();
			default -> event.aggregateId();
		};
	}

	private Map<String, Object> toPayload(DomainEvent event) {
		return switch (event) {
			case FraudCaseApprovedEvent e -> Map.of(
					"eventId", e.eventId(),
					"eventType", e.eventType(),
					"fraudCaseId", e.fraudCaseId().toString(),
					"paymentId", e.paymentId(),
					"riskScore", e.score().getValue(),
					"riskLevel", e.score().getLevel().name(),
					"occurredAt", e.occurredAt().toString()
			);
			case FraudCaseBlockedEvent e -> Map.of(
					"eventId", e.eventId(),
					"eventType", e.eventType(),
					"fraudCaseId", e.fraudCaseId().toString(),
					"paymentId", e.paymentId(),
					"sourceAccountId", e.sourceAccountId(),
					"riskScore", e.score().getValue(),
					"riskLevel", e.score().getLevel().name(),
					"reason", e.reason(),
					"occurredAt", e.occurredAt().toString()
			);
			case FraudCaseEscalatedEvent e -> Map.of(
					"eventId", e.eventId(),
					"eventType", e.eventType(),
					"fraudCaseId", e.fraudCaseId().toString(),
					"paymentId", e.paymentId(),
					"riskScore", e.score().getValue(),
					"occurredAt", e.occurredAt().toString()
			);
			case FraudConfirmedEvent e -> Map.of(
					"eventId", e.eventId(),
					"eventType", e.eventType(),
					"fraudCaseId", e.fraudCaseId().toString(),
					"paymentId", e.paymentId(),
					"sourceAccountId", e.sourceAccountId(),
					"riskScore", e.score().getValue(),
					"notes", e.notes(),
					"occurredAt", e.occurredAt().toString()
			);
			default -> Map.of(
					"eventId", event.eventId(),
					"eventType", event.eventType(),
					"aggregateId", event.aggregateId(),
					"occurredAt", event.occurredAt().toString()
			);
		};
	}
}