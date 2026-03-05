package com.matcodem.fincore.payment.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.matcodem.fincore.payment.domain.domain.port.out.OutboxEventPublisher;
import com.matcodem.fincore.payment.domain.domain.port.out.OutboxRepository;
import com.matcodem.fincore.payment.domain.event.DomainEvent;
import com.matcodem.fincore.payment.domain.model.OutboxMessage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Saves domain events to the outbox table within the current transaction.
 * The OutboxPoller will pick them up and publish to Kafka.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventPublisherImpl implements OutboxEventPublisher {

	private final OutboxRepository outboxRepository;
	private final ObjectMapper objectMapper;

	@Override
	public void publish(DomainEvent event, String aggregateType) {
		try {
			String payload = objectMapper.writeValueAsString(event);

			OutboxMessage message = OutboxMessage.create(
					event.aggregateId(),
					aggregateType,
					event.eventType(),
					payload
			);

			outboxRepository.save(message);
			log.debug("Saved outbox message for event: {} (aggregate: {})",
					event.eventType(), event.aggregateId());

		} catch (Exception ex) {
			// Failing to serialize is a programming error, not a transient issue
			throw new RuntimeException("Failed to serialize domain event: " + event.eventType(), ex);
		}
	}
}