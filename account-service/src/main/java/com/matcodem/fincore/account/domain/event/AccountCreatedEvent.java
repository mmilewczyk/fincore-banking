package com.matcodem.fincore.account.domain.event;

import java.time.Instant;
import java.util.UUID;

import com.matcodem.fincore.account.domain.model.AccountId;
import com.matcodem.fincore.account.domain.model.Currency;
import com.matcodem.fincore.account.domain.model.IBAN;

public record AccountCreatedEvent(
		UUID eventId,
		Instant occurredAt,
		AccountId accountId,
		String ownerId,
		IBAN iban,
		Currency currency,
		String email,
		String phoneNumber   // nullable
) implements DomainEvent {

	public AccountCreatedEvent(AccountId accountId, String ownerId, IBAN iban,
	                           Currency currency, String email, String phoneNumber,
	                           Instant occurredAt) {
		this(UUID.randomUUID(), occurredAt, accountId, ownerId, iban, currency, email, phoneNumber);
	}

	@Override
	public String aggregateId() {
		return accountId.toString();
	}

	@Override
	public String eventType() {
		return "account.created";
	}
}