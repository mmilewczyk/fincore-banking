package com.matcodem.fincore.payment.infrastructure.messaging;

import java.util.List;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.matcodem.fincore.payment.domain.domain.port.out.OutboxRepository;
import com.matcodem.fincore.payment.domain.model.OutboxMessage;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Outbox Poller — the second half of the Outbox Pattern.
 * <p>
 * Flow:
 * 1. PaymentApplicationService saves payment + OutboxMessage in one DB transaction
 * 2. This poller (runs every 1s) reads PENDING messages from outbox table
 * 3. Publishes each message to Kafka
 * 4. On success: marks message as SENT
 * 5. On failure: increments retryCount; after 5 failures → DEAD_LETTER
 * <p>
 * Uses SELECT FOR UPDATE SKIP LOCKED — safe to run on multiple instances:
 * → Each pod picks a different set of messages, no duplicates.
 * <p>
 * Why not use @TransactionalEventListener?
 * → It fires after TX commit, but if the app crashes between commit and Kafka send,
 * the event is lost. Outbox + polling survives crashes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPoller {

	private final OutboxRepository outboxRepository;
	private final KafkaTemplate<String, String> kafkaTemplate;
	private final MeterRegistry meterRegistry;

	private static final String TOPIC_PREFIX = "fincore.payments.";
	private static final int BATCH_SIZE = 50;

	@Scheduled(fixedDelay = 1000) // poll every 1 second
	public void pollAndPublish() {
		List<OutboxMessage> messages = outboxRepository.findPendingMessages(BATCH_SIZE);

		if (messages.isEmpty()) return;

		log.debug("Processing {} outbox messages", messages.size());

		for (OutboxMessage message : messages) {
			publishMessage(message);
		}
	}

	private void publishMessage(OutboxMessage message) {
		String topic = TOPIC_PREFIX + message.getEventType().replace(".", "-");

		try {
			kafkaTemplate.send(topic, message.getAggregateId(), message.getPayload())
					.whenComplete((result, ex) -> {
						if (ex != null) {
							log.error("Failed to publish outbox message {}: {}", message.getId(), ex.getMessage());
							outboxRepository.markFailed(message);
							meterRegistry.counter("outbox.publish.failed").increment();
						} else {
							outboxRepository.markSent(message);
							meterRegistry.counter("outbox.publish.success").increment();
							log.debug("Outbox message {} sent to topic {}", message.getId(), topic);
						}
					});
		} catch (Exception ex) {
			log.error("Error publishing outbox message {}: {}", message.getId(), ex.getMessage(), ex);
			outboxRepository.markFailed(message);
			meterRegistry.counter("outbox.publish.error").increment();
		}
	}
}