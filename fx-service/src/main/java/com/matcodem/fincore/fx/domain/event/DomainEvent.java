package com.matcodem.fincore.fx.domain.event;

import java.time.Instant;
import java.util.UUID;

public interface DomainEvent {
	UUID eventId();

	Instant occurredAt();

	String aggregateId();

	String eventType();
}
