package com.matcodem.fincore.account.domain.event;

import java.time.Instant;
import java.util.UUID;

import com.matcodem.fincore.account.domain.model.AccountId;
import com.matcodem.fincore.account.domain.model.Money;

public record AccountDebitedEvent(
		UUID eventId,
		Instant occurredAt,
		AccountId accountId,
		Money amount,
		Money balanceAfter,
		String reference
) implements DomainEvent {

	public AccountDebitedEvent(AccountId accountId, Money amount, Money balanceAfter,
	                           String reference, Instant occurredAt) {
		this(UUID.randomUUID(), occurredAt, accountId, amount, balanceAfter, reference);
	}

	@Override
	public String aggregateId() {
		return accountId.toString();
	}

	@Override
	public String eventType() {
		return "account.debited";
	}
}
