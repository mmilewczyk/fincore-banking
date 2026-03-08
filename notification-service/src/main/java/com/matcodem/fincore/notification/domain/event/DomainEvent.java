package com.matcodem.fincore.notification.domain.event;

import java.time.Instant;
import java.util.UUID;

public interface DomainEvent {
	UUID eventId();

	Instant occurredAt();

	String eventType();
}
