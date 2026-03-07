package com.matcodem.fincore.payment.infrastructure.messaging;

import java.util.List;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.matcodem.fincore.payment.domain.model.OutboxMessage;
import com.matcodem.fincore.payment.domain.port.out.OutboxRepository;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Outbox Processor — the missing piece that makes at-least-once delivery real.
 * <p>
 * Pattern:
 * 1. Business operation saves Payment + OutboxMessage in ONE DB transaction
 * 2. This scheduler polls PENDING outbox messages and publishes them to Kafka
 * 3. On ACK from Kafka → mark SENT
 * 4. On failure → increment retry count; after 5 attempts → DEAD_LETTER
 * <p>
 * Concurrency safety:
 * SELECT FOR UPDATE SKIP LOCKED ensures that if multiple instances of
 * payment-service are running (K8s replicas), each message is processed
 * by exactly one instance. The lock is held for the duration of the
 * publish + update, then released.
 * <p>
 * Topic routing:
 * eventType "payment.initiated"   → fincore.payments.payment-initiated
 * eventType "payment.completed"   → fincore.payments.payment-completed
 * eventType "payment.failed"      → fincore.payments.payment-failed
 * eventType "payment.fraud.*"     → fincore.payments.payment-fraud-rejected
 * eventType "payment.cancelled"   → fincore.payments.payment-cancelled
 * <p>
 * Kafka key = aggregateId (paymentId) → ordering guaranteed per payment
 * within a partition.
 * <p>
 * Monitoring:
 * outbox.published.total   — successful publishes
 * outbox.failed.total      — publish failures (retriable)
 * outbox.dead_letter.total — messages that exhausted retries
 * outbox.pending.gauge     — current backlog (alert if > threshold)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPoller {

	private final OutboxRepository outboxRepository;
	private final KafkaTemplate<String, String> kafkaTemplate;
	private final MeterRegistry meterRegistry;

	private static final String TOPIC_PREFIX = "fincore.payments.";
	private static final int BATCH_SIZE = 100;

	/**
	 * Poll every 500ms. Fixed delay (not rate) — next run starts after
	 * current run + 500ms, preventing overlap under load.
	 * <p>
	 * Under normal load: 100 messages/500ms = 200 msg/s throughput.
	 * If backlog grows, increase BATCH_SIZE or decrease fixedDelay.
	 */
	@Scheduled(fixedDelay = 500)
	@Transactional
	public void pollAndPublish() {
		List<OutboxMessage> messages = outboxRepository.findPendingMessages(BATCH_SIZE);

		if (messages.isEmpty()) return;

		log.debug("Processing {} outbox messages", messages.size());

		for (OutboxMessage message : messages) {
			processMessage(message);
		}
	}

	private void processMessage(OutboxMessage message) {
		String topic = resolveTopic(message.getEventType());

		try {
			// sendSync() blocks until broker ACKs — guarantees the message
			// is durable before we mark it SENT in the same transaction.
			kafkaTemplate.send(topic, message.getAggregateId(), message.getPayload())
					.get(); // blocks for broker ack

			message.markSent();
			outboxRepository.markSent(message);

			meterRegistry.counter("outbox.published.total",
					"eventType", message.getEventType()).increment();

			log.debug("Outbox published: {} → {} (aggregateId: {})",
					message.getEventType(), topic, message.getAggregateId());

		} catch (Exception ex) {
			log.error("Outbox publish failed for message {} (retry {}): {}",
					message.getId(), message.getRetryCount(), ex.getMessage());

			message.markFailed();
			outboxRepository.markFailed(message);

			if (message.isDeadLetter()) {
				log.error("DEAD LETTER — message {} exhausted retries. eventType={}, aggregateId={}",
						message.getId(), message.getEventType(), message.getAggregateId());
				meterRegistry.counter("outbox.dead_letter.total",
						"eventType", message.getEventType()).increment();
			} else {
				meterRegistry.counter("outbox.failed.total",
						"eventType", message.getEventType()).increment();
			}
		}
	}

	/**
	 * Maps domain event type to Kafka topic name.
	 * <p>
	 * Convention: eventType uses dots (payment.completed),
	 * topic uses hyphens (payment-completed) under fincore.payments. prefix.
	 */
	private String resolveTopic(String eventType) {
		// payment.initiated       → fincore.payments.payment-initiated
		// payment.completed       → fincore.payments.payment-completed
		// payment.failed          → fincore.payments.payment-failed
		// payment.fraud.rejected  → fincore.payments.payment-fraud-rejected
		// payment.cancelled       → fincore.payments.payment-cancelled
		String suffix = eventType.replace(".", "-");
		return TOPIC_PREFIX + suffix;
	}
}