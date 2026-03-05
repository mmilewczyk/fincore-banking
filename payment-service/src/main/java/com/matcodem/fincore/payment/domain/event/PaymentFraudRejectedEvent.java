package com.matcodem.fincore.payment.domain.event;

import java.time.Instant;
import java.util.UUID;

import com.matcodem.fincore.payment.domain.model.Money;
import com.matcodem.fincore.payment.domain.model.PaymentId;

public record PaymentFraudRejectedEvent(
		UUID eventId,
		Instant occurredAt,
		PaymentId paymentId,
		String sourceAccountId,
		Money amount,
		String reason
) implements DomainEvent {

	public PaymentFraudRejectedEvent(PaymentId paymentId, String sourceAccountId,
	                                 Money amount, String reason, Instant occurredAt) {
		this(UUID.randomUUID(), occurredAt, paymentId, sourceAccountId, amount, reason);
	}

	@Override
	public String aggregateId() {
		return paymentId.toString();
	}

	@Override
	public String eventType() {
		return "payment.fraud-rejected";
	}
}