package com.matcodem.fincore.payment.domain.event;

import java.time.Instant;
import java.util.UUID;

public interface DomainEvent {
	UUID eventId();

	Instant occurredAt();

	String aggregateId();

	String eventType();
}