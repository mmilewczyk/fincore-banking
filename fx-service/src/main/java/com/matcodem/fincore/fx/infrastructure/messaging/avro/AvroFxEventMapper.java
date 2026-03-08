package com.matcodem.fincore.fx.infrastructure.messaging.avro;

import org.apache.avro.specific.SpecificRecord;
import org.springframework.stereotype.Component;

import com.matcodem.fincore.fx.avro.FxFailureCategory;
import com.matcodem.fincore.fx.domain.event.DomainEvent;
import com.matcodem.fincore.fx.domain.event.ExchangeRatePublishedEvent;
import com.matcodem.fincore.fx.domain.event.FxConversionExecutedEvent;
import com.matcodem.fincore.fx.domain.event.FxConversionFailedEvent;

@Component
public class AvroFxEventMapper {

	public SpecificRecord toAvro(DomainEvent event) {
		return switch (event) {
			case ExchangeRatePublishedEvent e -> toAvro(e);
			case FxConversionExecutedEvent e -> toAvro(e);
			case FxConversionFailedEvent e -> toAvro(e);
			default -> throw new IllegalStateException(
					"No Avro mapping for FX event type: " + event.eventType());
		};
	}

	public com.matcodem.fincore.fx.avro.ExchangeRatePublishedEvent toAvro(ExchangeRatePublishedEvent e) {
		return com.matcodem.fincore.fx.avro.ExchangeRatePublishedEvent.newBuilder()
				.setEventId(e.eventId().toString())
				.setOccurredAt(e.occurredAt())
				.setRateId(e.rateId().toString())
				.setCurrencyPair(e.pair().getSymbol())
				.setMidRate(e.midRate())
				.setBidRate(e.bidRate())
				.setAskRate(e.askRate())
				.setSpreadBasisPoints(e.spreadBasisPoints())
				.setSchemaVersion(1)
				.build();
	}

	public com.matcodem.fincore.fx.avro.FxConversionExecutedEvent toAvro(FxConversionExecutedEvent e) {
		return com.matcodem.fincore.fx.avro.FxConversionExecutedEvent.newBuilder()
				.setEventId(e.eventId().toString())
				.setOccurredAt(e.occurredAt())
				.setConversionId(e.conversionId().toString())
				.setPaymentId(e.paymentId())
				.setAccountId(e.accountId())
				.setCurrencyPair(e.pair().getSymbol())
				.setSourceAmount(e.sourceAmount())
				.setConvertedAmount(e.convertedAmount())
				.setAppliedRate(e.appliedRate())
				.setFee(e.fee())
				.setSchemaVersion(1)
				.build();
	}

	public com.matcodem.fincore.fx.avro.FxConversionFailedEvent toAvro(FxConversionFailedEvent e) {
		return com.matcodem.fincore.fx.avro.FxConversionFailedEvent.newBuilder()
				.setEventId(e.eventId().toString())
				.setOccurredAt(e.occurredAt())
				.setConversionId(e.conversionId().toString())
				.setPaymentId(e.paymentId())
				.setCurrencyPair(e.pair().getSymbol())
				.setSourceAmount(e.sourceAmount())
				.setReason(e.reason())
				.setFailureCategory(classifyFailure(e.reason()))
				.setSchemaVersion(1)
				.build();
	}

	private FxFailureCategory classifyFailure(String reason) {
		if (reason == null) return FxFailureCategory.UNKNOWN;
		String lower = reason.toLowerCase();
		if (lower.contains("circuit breaker") || lower.contains("cb open"))
			return FxFailureCategory.CIRCUIT_BREAKER_OPEN;
		if (lower.contains("rate unavailable") || lower.contains("no rate")) return FxFailureCategory.RATE_UNAVAILABLE;
		if (lower.contains("provider")) return FxFailureCategory.PROVIDER_ERROR;
		if (lower.contains("too large") || lower.contains("exceeds limit")) return FxFailureCategory.AMOUNT_TOO_LARGE;
		return FxFailureCategory.UNKNOWN;
	}
}