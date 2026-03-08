package com.matcodem.fincore.notification.domain.model;

import java.util.Objects;
import java.util.UUID;

public record NotificationId(UUID value) {
	public NotificationId {
		Objects.requireNonNull(value, "NotificationId value required");
	}

	public static NotificationId generate() {
		return new NotificationId(UUID.randomUUID());
	}

	public static NotificationId of(UUID value) {
		return new NotificationId(value);
	}

	public static NotificationId of(String value) {
		return new NotificationId(UUID.fromString(value));
	}

	@Override
	public String toString() {
		return value.toString();
	}
}