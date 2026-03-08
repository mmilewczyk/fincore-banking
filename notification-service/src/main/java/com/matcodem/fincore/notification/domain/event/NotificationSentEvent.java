package com.matcodem.fincore.notification.domain.event;

import java.time.Instant;
import java.util.UUID;

import com.matcodem.fincore.notification.domain.model.NotificationChannel;
import com.matcodem.fincore.notification.domain.model.NotificationId;
import com.matcodem.fincore.notification.domain.model.NotificationType;

public record NotificationSentEvent(
		UUID eventId, Instant occurredAt,
		NotificationId notificationId,
		String recipientUserId,
		NotificationType type,
		NotificationChannel channel
) implements DomainEvent {
	public NotificationSentEvent(NotificationId id, String userId,
	                             NotificationType type, NotificationChannel channel,
	                             Instant occurredAt) {
		this(UUID.randomUUID(), occurredAt, id, userId, type, channel);
	}

	@Override
	public String eventType() {
		return "notification.sent";
	}
}
