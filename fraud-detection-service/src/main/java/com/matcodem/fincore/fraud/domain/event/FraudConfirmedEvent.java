package com.matcodem.fincore.fraud.domain.event;

import java.time.Instant;
import java.util.UUID;

import com.matcodem.fincore.fraud.domain.model.FraudCaseId;
import com.matcodem.fincore.fraud.domain.model.RiskScore;

public record FraudConfirmedEvent(
		UUID eventId, Instant occurredAt,
		FraudCaseId fraudCaseId, String paymentId,
		String sourceAccountId, RiskScore score, String notes
) implements DomainEvent {

	public FraudConfirmedEvent(FraudCaseId fraudCaseId, String paymentId,
	                           String sourceAccountId, RiskScore score,
	                           String notes, Instant occurredAt) {
		this(UUID.randomUUID(), occurredAt, fraudCaseId, paymentId, sourceAccountId, score, notes);
	}

	@Override
	public String aggregateId() {
		return fraudCaseId.toString();
	}

	@Override
	public String eventType() {
		return "fraud.confirmed";
	}
}
