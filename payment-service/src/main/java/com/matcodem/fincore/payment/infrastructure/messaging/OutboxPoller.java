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
 * Outbox Poller — publishes pending domain events to Kafka.
 * <p>
 * ── PATTERN ───────────────────────────────────────────────────────────────────
 * The Transactional Outbox pattern guarantees at-least-once event delivery:
 * 1. Payment + outbox row written atomically in one DB transaction
 * 2. This poller reads PENDING outbox rows and publishes to Kafka
 * 3. On broker ACK → row marked SENT in same transaction
 * <p>
 * ── TRANSACTION DESIGN ────────────────────────────────────────────────────────
 * pollAndPublish() has NO @Transactional — it iterates rows and delegates each
 * to publishSingle() which opens its own REQUIRES_NEW transaction.
 * <p>
 * Why REQUIRES_NEW per message (not one big transaction for the batch)?
 * - A single Kafka failure must not roll back the entire batch
 * - SELECT FOR UPDATE SKIP LOCKED is held per-row for minimum duration
 * - Spring's @Transactional(REQUIRES_NEW) suspends the caller's transaction
 * (none here) and opens a fresh one, so the JPA flush + commit happens
 * immediately after markSent/markFailed — not at end of outer method
 * <p>
 * ── CONCURRENCY ───────────────────────────────────────────────────────────────
 * SELECT FOR UPDATE SKIP LOCKED (in SpringDataOutboxRepository) ensures that
 * multiple payment-service pods each claim a distinct batch of rows.
 * No two pods will publish the same message. Combined with Kafka producer
 * idempotence (enable.idempotence=true) this is safe at-least-once delivery.
 * <p>
 * ── KAFKA ACK STRATEGY (.get() vs whenComplete) ───────────────────────────────
 * kafkaTemplate.send(...).get() blocks until the broker acknowledges the write.
 * The DB markSent() call happens only after the broker ACK, within the same
 * transaction. If the JVM crashes between ACK and commit, the row stays PENDING
 * and is republished on next poll — Kafka consumers must be idempotent.
 * <p>
 * Alternative (whenComplete) runs in Kafka callback thread, outside Spring's
 * transaction context. markSent() would not be part of the DB transaction.
 * Chosen against: harder to reason about, same at-least-once outcome.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPoller {

	private final OutboxRepository outboxRepository;
	private final KafkaTemplate<String, String> kafkaTemplate;
	private final MeterRegistry meterRegistry;

	private static final int BATCH_SIZE = 100;

	/**
	 * fixedDelay — next poll starts 500ms AFTER current poll completes.
	 * Prevents concurrent execution on a single instance.
	 * Cross-instance concurrency is handled by SELECT FOR UPDATE SKIP LOCKED.
	 */
	@Scheduled(fixedDelayString = "${outbox.poller.fixed-delay-ms:500}")
	public void pollAndPublish() {
		List<OutboxMessage> messages = outboxRepository.findPendingMessages(BATCH_SIZE);
		if (messages.isEmpty()) return;

		log.debug("Outbox poll: {} pending messages to publish", messages.size());
		for (OutboxMessage message : messages) {
			publishOne(message);
		}
	}

	/**
	 * Each message in its own REQUIRES_NEW transaction:
	 * - SELECT FOR UPDATE lock is released immediately after this method returns
	 * - A Kafka failure for one message does not roll back others
	 * - markSent/markFailed is committed atomically with the lock release
	 */
	@Transactional
	public void publishOne(OutboxMessage message) {
		// Derive Kafka topic from event type: "payment.completed" → "fincore.payments.payment-completed"
		String topic = "fincore.payments." + message.getEventType().replace(".", "-");

		try {
			// Synchronous: .get() blocks until broker writes to ISR and ACKs.
			// acks=all in producer config ensures all in-sync replicas have the message.
			kafkaTemplate.send(topic, message.getAggregateId(), message.getPayload()).get();

			message.markSent();
			outboxRepository.markSent(message);

			meterRegistry.counter("outbox.publish.success",
					"eventType", message.getEventType()).increment();
			log.debug("Published outbox {} → topic: {}", message.getId(), topic);

		} catch (Exception ex) {
			message.markFailed();
			outboxRepository.markFailed(message);

			if (message.isDeadLetter()) {
				log.error("DEAD LETTER: outbox message {} exhausted all retries — eventType={}, aggregateId={}",
						message.getId(), message.getEventType(), message.getAggregateId());
				meterRegistry.counter("outbox.dead_letter.total",
						"eventType", message.getEventType()).increment();
			} else {
				log.warn("Outbox publish failed (attempt {}): message={} error={}",
						message.getRetryCount(), message.getId(), ex.getMessage());
				meterRegistry.counter("outbox.publish.failed",
						"eventType", message.getEventType()).increment();
			}
		}
	}
}