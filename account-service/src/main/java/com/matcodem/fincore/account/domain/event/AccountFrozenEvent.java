package com.matcodem.fincore.account.domain.event;

import java.time.Instant;
import java.util.UUID;

import com.matcodem.fincore.account.domain.model.AccountId;

public record AccountFrozenEvent(
		UUID eventId,
		Instant occurredAt,
		AccountId accountId,
		String reason
) implements DomainEvent {

	public AccountFrozenEvent(AccountId accountId, String reason, Instant occurredAt) {
		this(UUID.randomUUID(), occurredAt, accountId, reason);
	}

	@Override
	public String aggregateId() {
		return accountId.toString();
	}

	@Override
	public String eventType() {
		return "account.frozen";
	}
}