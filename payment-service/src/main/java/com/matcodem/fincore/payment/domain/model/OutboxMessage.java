package com.matcodem.fincore.payment.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Outbox Pattern - guaranteed event delivery.
 * <p>
 * Problem it solves:
 * Without outbox, we risk: payment saved to DB but Kafka publish fails
 * -> payment processed but nobody notified -> data inconsistency.
 * <p>
 * Solution:
 * 1. Save payment + outbox message in ONE transaction (same DB)
 * 2. Separate OutboxPoller reads unsent messages and publishes to Kafka
 * 3. On successful publish, mark message as SENT
 * <p>
 * This guarantees at-least-once delivery. Consumers must be idempotent.
 */
public class OutboxMessage {

	private final UUID id;
	private final String aggregateId;
	private final String aggregateType;
	private final String eventType;
	private final String payload;          // JSON serialized event
	private OutboxStatus status;
	private int retryCount;
	private Instant createdAt;
	private Instant processedAt;

	private OutboxMessage(UUID id, String aggregateId, String aggregateType,
	                      String eventType, String payload, OutboxStatus status,
	                      int retryCount, Instant createdAt, Instant processedAt) {
		this.id = id;
		this.aggregateId = aggregateId;
		this.aggregateType = aggregateType;
		this.eventType = eventType;
		this.payload = payload;
		this.status = status;
		this.retryCount = retryCount;
		this.createdAt = createdAt;
		this.processedAt = processedAt;
	}

	public static OutboxMessage create(String aggregateId, String aggregateType,
	                                   String eventType, String payload) {
		Objects.requireNonNull(aggregateId, "AggregateId required");
		Objects.requireNonNull(eventType, "EventType required");
		Objects.requireNonNull(payload, "Payload required");

		return new OutboxMessage(
				UUID.randomUUID(), aggregateId, aggregateType,
				eventType, payload, OutboxStatus.PENDING,
				0, Instant.now(), null
		);
	}

	public static OutboxMessage reconstitute(UUID id, String aggregateId, String aggregateType,
	                                         String eventType, String payload, OutboxStatus status,
	                                         int retryCount, Instant createdAt, Instant processedAt) {
		return new OutboxMessage(id, aggregateId, aggregateType, eventType, payload,
				status, retryCount, createdAt, processedAt);
	}

	public void markSent() {
		this.status = OutboxStatus.SENT;
		this.processedAt = Instant.now();
	}

	public void markFailed() {
		this.retryCount++;
		if (this.retryCount >= 5) {
			this.status = OutboxStatus.DEAD_LETTER;
		}
		// else stays PENDING for retry
	}

	public boolean isReadyForRetry() {
		return status == OutboxStatus.PENDING;
	}

	public boolean isDeadLetter() {
		return status == OutboxStatus.DEAD_LETTER;
	}

	public UUID getId() {
		return id;
	}

	public String getAggregateId() {
		return aggregateId;
	}

	public String getAggregateType() {
		return aggregateType;
	}

	public String getEventType() {
		return eventType;
	}

	public String getPayload() {
		return payload;
	}

	public OutboxStatus getStatus() {
		return status;
	}

	public int getRetryCount() {
		return retryCount;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getProcessedAt() {
		return processedAt;
	}

	public enum OutboxStatus {
		PENDING,
		SENT,
		DEAD_LETTER
	}
}