package com.matcodem.fincore.payment.domain.event;

import java.time.Instant;
import java.util.UUID;

import com.matcodem.fincore.payment.domain.model.Money;
import com.matcodem.fincore.payment.domain.model.PaymentId;
import com.matcodem.fincore.payment.domain.model.PaymentType;

public record PaymentCompletedEvent(
		UUID eventId,
		Instant occurredAt,
		PaymentId paymentId,
		String sourceAccountId,
		String targetAccountId,
		Money amount,
		PaymentType type
) implements DomainEvent {

	public PaymentCompletedEvent(PaymentId paymentId, String sourceAccountId,
	                             String targetAccountId, Money amount, Instant occurredAt, PaymentType paymentType) {
		this(UUID.randomUUID(), occurredAt, paymentId, sourceAccountId, targetAccountId, amount, paymentType);
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