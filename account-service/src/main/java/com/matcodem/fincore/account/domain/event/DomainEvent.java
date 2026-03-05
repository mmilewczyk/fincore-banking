package com.matcodem.fincore.account.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Base domain event interface.
 * All domain events are immutable records of something that happened.
 */
public interface DomainEvent {
	UUID eventId();
	Instant occurredAt();
	String aggregateId();
	String eventType();
}
