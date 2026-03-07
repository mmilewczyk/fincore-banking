package com.matcodem.fincore.fx.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.matcodem.fincore.fx.domain.model.CurrencyPair;
import com.matcodem.fincore.fx.domain.model.FxConversionId;

public record FxConversionFailedEvent(
		UUID eventId, Instant occurredAt,
		FxConversionId conversionId, String paymentId,
		CurrencyPair pair, BigDecimal sourceAmount, String reason
) implements DomainEvent {
	public FxConversionFailedEvent(FxConversionId conversionId, String paymentId,
	                               CurrencyPair pair, BigDecimal sourceAmount,
	                               String reason, Instant occurredAt) {
		this(UUID.randomUUID(), occurredAt, conversionId, paymentId, pair, sourceAmount, reason);
	}

	@Override
	public String aggregateId() {
		return conversionId.toString();
	}

	@Override
	public String eventType() {
		return "fx.conversion.failed";
	}
}
