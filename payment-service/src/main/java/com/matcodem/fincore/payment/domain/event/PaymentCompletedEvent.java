package com.matcodem.fincore.payment.domain.event;

import java.time.Instant;
import java.util.UUID;

import com.matcodem.fincore.payment.domain.model.Money;
import com.matcodem.fincore.payment.domain.model.PaymentId;

public record PaymentCompletedEvent(
		UUID eventId,
		Instant occurredAt,
		PaymentId paymentId,
		String sourceAccountId,
		String targetAccountId,
		Money amount
) implements DomainEvent {

	public PaymentCompletedEvent(PaymentId paymentId, String sourceAccountId,
	                             String targetAccountId, Money amount, Instant occurredAt) {
		this(UUID.randomUUID(), occurredAt, paymentId, sourceAccountId, targetAccountId, amount);
	}

	@Override
	public String aggregateId() {
		return paymentId.toString();
	}

	@Override
	public String eventType() {
		return "payment.completed";
	}
}