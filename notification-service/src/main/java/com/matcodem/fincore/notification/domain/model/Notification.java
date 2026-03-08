package com.matcodem.fincore.notification.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.matcodem.fincore.notification.domain.event.DomainEvent;
import com.matcodem.fincore.notification.domain.event.NotificationFailedEvent;
import com.matcodem.fincore.notification.domain.event.NotificationSentEvent;

/**
 * Notification Aggregate Root.
 * <p>
 * One Notification represents ONE delivery attempt on ONE channel.
 * If a payment completion triggers Email + Push, two Notification aggregates are created.
 * <p>
 * WHY one-per-channel (not one with multiple channels)?
 * - Independent retry: Email can be retried without re-sending Push
 * - Independent status: Email=SENT, Push=FAILED is representable
 * - Clean audit: each delivery has its own record, timestamps, failure reason
 * - Simpler outbox: one outbox row -> one notification dispatch (no fan-out complexity)
 * <p>
 * Lifecycle: PENDING -> SENT | FAILED -> DEAD_LETTER (after max retries)
 * <p>
 * retryCount tracks delivery attempts for the outbox poller.
 * Max retries enforced in NotificationOutboxPoller, not here - aggregate has no
 * knowledge of retry policy (that's infrastructure concern).
 */
public class Notification {

	private static final int MAX_RETRY_COUNT = 5;

	private final NotificationId id;
	private final String correlationEventId;  // source domain event ID (for deduplication)
	private final String recipientUserId;
	private final NotificationType type;
	private final NotificationChannel channel;
	private final RecipientContact contact;
	private final NotificationPayload payload;
	private NotificationStatus status;
	private String failureReason;
	private int retryCount;
	private final Instant createdAt;
	private Instant updatedAt;

	private final List<DomainEvent> domainEvents = new ArrayList<>();

	private Notification(NotificationId id, String correlationEventId,
	                     String recipientUserId, NotificationType type,
	                     NotificationChannel channel, RecipientContact contact,
	                     NotificationPayload payload, NotificationStatus status,
	                     String failureReason, int retryCount,
	                     Instant createdAt, Instant updatedAt) {
		this.id = id;
		this.correlationEventId = correlationEventId;
		this.recipientUserId = recipientUserId;
		this.type = type;
		this.channel = channel;
		this.contact = contact;
		this.payload = payload;
		this.status = status;
		this.failureReason = failureReason;
		this.retryCount = retryCount;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}

	public static Notification create(
			String correlationEventId,
			String recipientUserId,
			NotificationType type,
			NotificationChannel channel,
			RecipientContact contact,
			NotificationPayload payload) {

		Objects.requireNonNull(correlationEventId, "correlationEventId required");
		Objects.requireNonNull(recipientUserId, "recipientUserId required");
		Objects.requireNonNull(type, "type required");
		Objects.requireNonNull(channel, "channel required");
		Objects.requireNonNull(contact, "contact required");
		Objects.requireNonNull(payload, "payload required");

		Instant now = Instant.now();
		return new Notification(
				NotificationId.generate(), correlationEventId,
				recipientUserId, type, channel, contact, payload,
				NotificationStatus.PENDING, null, 0, now, now
		);
	}

	public static Notification reconstitute(
			NotificationId id, String correlationEventId,
			String recipientUserId, NotificationType type,
			NotificationChannel channel, RecipientContact contact,
			NotificationPayload payload, NotificationStatus status,
			String failureReason, int retryCount,
			Instant createdAt, Instant updatedAt) {
		return new Notification(id, correlationEventId, recipientUserId,
				type, channel, contact, payload, status, failureReason,
				retryCount, createdAt, updatedAt);
	}

	/**
	 * Marks this notification as successfully sent by a channel provider.
	 * Terminal state - cannot be retried or overwritten.
	 */
	public void markSent() {
		if (status == NotificationStatus.SENT) {
			return; // idempotent - already sent, no-op
		}
		if (status == NotificationStatus.DEAD_LETTER) {
			throw new IllegalStateException(
					"Cannot mark DEAD_LETTER notification as sent: " + id);
		}
		this.status = NotificationStatus.SENT;
		this.failureReason = null;
		this.updatedAt = Instant.now();
		recordEvent(new NotificationSentEvent(id, recipientUserId, type, channel, updatedAt));
	}

	/**
	 * Records a failed delivery attempt.
	 * If max retries reached, transitions to DEAD_LETTER.
	 * Otherwise stays FAILED - outbox poller will retry.
	 */
	public void markFailed(String reason) {
		Objects.requireNonNull(reason, "failure reason required");
		if (status == NotificationStatus.SENT || status == NotificationStatus.DEAD_LETTER) {
			throw new IllegalStateException(
					"Cannot mark terminal notification as failed: %s (status: %s)".formatted(id, status));
		}
		this.retryCount++;
		this.failureReason = reason;
		this.updatedAt = Instant.now();

		if (retryCount >= MAX_RETRY_COUNT) {
			this.status = NotificationStatus.DEAD_LETTER;
			recordEvent(new NotificationFailedEvent(id, recipientUserId, type, channel,
					reason, true, updatedAt));
		} else {
			this.status = NotificationStatus.FAILED;
			recordEvent(new NotificationFailedEvent(id, recipientUserId, type, channel,
					reason, false, updatedAt));
		}
	}

	public boolean isRetryable() {
		return status == NotificationStatus.FAILED || status == NotificationStatus.PENDING;
	}

	public boolean isDeadLetter() {
		return status == NotificationStatus.DEAD_LETTER;
	}

	private void recordEvent(DomainEvent event) {
		domainEvents.add(event);
	}

	public List<DomainEvent> pullDomainEvents() {
		List<DomainEvent> events = new ArrayList<>(domainEvents);
		domainEvents.clear();
		return Collections.unmodifiableList(events);
	}

	public NotificationId getId() {
		return id;
	}

	public String getCorrelationEventId() {
		return correlationEventId;
	}

	public String getRecipientUserId() {
		return recipientUserId;
	}

	public NotificationType getType() {
		return type;
	}

	public NotificationChannel getChannel() {
		return channel;
	}

	public RecipientContact getContact() {
		return contact;
	}

	public NotificationPayload getPayload() {
		return payload;
	}

	public NotificationStatus getStatus() {
		return status;
	}

	public String getFailureReason() {
		return failureReason;
	}

	public int getRetryCount() {
		return retryCount;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof Notification n)) return false;
		return Objects.equals(id, n.id);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id);
	}

	@Override
	public String toString() {
		return "Notification{id=%s, type=%s, channel=%s, status=%s}".formatted(id, type, channel, status);
	}
}