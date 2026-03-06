package com.matcodem.fincore.fraud.domain.event;

import java.time.Instant;
import java.util.UUID;

import com.matcodem.fincore.fraud.domain.model.FraudCaseId;
import com.matcodem.fincore.fraud.domain.model.RiskScore;

public record FraudCaseBlockedEvent(
		UUID eventId, Instant occurredAt,
		FraudCaseId fraudCaseId, String paymentId,
		RiskScore score, String reason, String sourceAccountId
) implements DomainEvent {

	public FraudCaseBlockedEvent(FraudCaseId fraudCaseId, String paymentId,
	                             RiskScore score, String reason,
	                             String sourceAccountId, Instant occurredAt) {
		this(UUID.randomUUID(), occurredAt, fraudCaseId, paymentId, score, reason, sourceAccountId);
	}

	@Override
	public String aggregateId() {
		return fraudCaseId.toString();
	}

	@Override
	public String eventType() {
		return "fraud.case.blocked";
	}
}
