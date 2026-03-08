package com.matcodem.fincore.notification.domain.event;

import java.time.Instant;
import java.util.UUID;

import com.matcodem.fincore.notification.domain.model.NotificationChannel;
import com.matcodem.fincore.notification.domain.model.NotificationId;
import com.matcodem.fincore.notification.domain.model.NotificationType;

public record NotificationFailedEvent(
		UUID eventId, Instant occurredAt,
		NotificationId notificationId,
		String recipientUserId,
		NotificationType type,
		NotificationChannel channel,
		String reason,
		boolean isDeadLetter  // true = max retries exceeded
) implements DomainEvent {
	public NotificationFailedEvent(NotificationId id, String userId,
	                               NotificationType type, NotificationChannel channel,
	                               String reason, boolean isDeadLetter, Instant occurredAt) {
		this(UUID.randomUUID(), occurredAt, id, userId, type, channel, reason, isDeadLetter);
	}

	@Override
	public String eventType() {
		return "notification.failed";
	}
}
