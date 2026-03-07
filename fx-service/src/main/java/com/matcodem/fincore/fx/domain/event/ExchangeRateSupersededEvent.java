package com.matcodem.fincore.fx.domain.event;

import java.time.Instant;
import java.util.UUID;

import com.matcodem.fincore.fx.domain.model.CurrencyPair;
import com.matcodem.fincore.fx.domain.model.ExchangeRateId;

public record ExchangeRateSupersededEvent(
		UUID eventId, Instant occurredAt,
		ExchangeRateId oldRateId, CurrencyPair pair, ExchangeRateId newRateId
) implements DomainEvent {
	public ExchangeRateSupersededEvent(ExchangeRateId oldRateId, CurrencyPair pair,
	                                   ExchangeRateId newRateId, Instant occurredAt) {
		this(UUID.randomUUID(), occurredAt, oldRateId, pair, newRateId);
	}

	@Override
	public String aggregateId() {
		return oldRateId.toString();
	}

	@Override
	public String eventType() {
		return "fx.rate.superseded";
	}
}