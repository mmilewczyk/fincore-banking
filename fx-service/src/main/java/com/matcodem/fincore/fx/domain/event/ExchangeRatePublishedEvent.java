package com.matcodem.fincore.fx.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.matcodem.fincore.fx.domain.model.CurrencyPair;
import com.matcodem.fincore.fx.domain.model.ExchangeRateId;

public record ExchangeRatePublishedEvent(
		UUID eventId,
		Instant occurredAt,
		ExchangeRateId rateId,
		CurrencyPair pair,
		BigDecimal midRate,
		BigDecimal bidRate,
		BigDecimal askRate,
		int spreadBasisPoints
) implements DomainEvent {

	public ExchangeRatePublishedEvent(ExchangeRateId rateId, CurrencyPair pair,
	                                  BigDecimal midRate, BigDecimal bidRate,
	                                  BigDecimal askRate, int spreadBasisPoints,
	                                  Instant occurredAt) {
		this(UUID.randomUUID(), occurredAt, rateId, pair, midRate, bidRate, askRate, spreadBasisPoints);
	}

	@Override
	public String aggregateId() {
		return rateId.toString();
	}

	@Override
	public String eventType() {
		return "fx.rate.published";
	}
}
