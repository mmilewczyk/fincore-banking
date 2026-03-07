package com.matcodem.fincore.fx.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.matcodem.fincore.fx.domain.model.CurrencyPair;
import com.matcodem.fincore.fx.domain.model.FxConversionId;

public record FxConversionExecutedEvent(
		UUID eventId, Instant occurredAt,
		FxConversionId conversionId, String paymentId, String accountId,
		CurrencyPair pair, BigDecimal sourceAmount, BigDecimal convertedAmount,
		BigDecimal appliedRate, BigDecimal fee
) implements DomainEvent {
	public FxConversionExecutedEvent(FxConversionId conversionId, String paymentId,
	                                 String accountId, CurrencyPair pair,
	                                 BigDecimal sourceAmount, BigDecimal convertedAmount,
	                                 BigDecimal appliedRate, BigDecimal fee, Instant occurredAt) {
		this(UUID.randomUUID(), occurredAt, conversionId, paymentId, accountId,
				pair, sourceAmount, convertedAmount, appliedRate, fee);
	}

	@Override
	public String aggregateId() {
		return conversionId.toString();
	}

	@Override
	public String eventType() {
		return "fx.conversion.executed";
	}
}