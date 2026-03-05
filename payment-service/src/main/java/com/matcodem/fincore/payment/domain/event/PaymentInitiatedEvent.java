package com.matcodem.fincore.payment.domain.event;

import java.time.Instant;
import java.util.UUID;

import com.matcodem.fincore.payment.domain.model.IdempotencyKey;
import com.matcodem.fincore.payment.domain.model.Money;
import com.matcodem.fincore.payment.domain.model.PaymentId;
import com.matcodem.fincore.payment.domain.model.PaymentType;

public record PaymentInitiatedEvent(
		UUID eventId,
		Instant occurredAt,
		PaymentId paymentId,
		IdempotencyKey idempotencyKey,
		String sourceAccountId,
		String targetAccountId,
		Money amount,
		PaymentType type,
		String initiatedBy
) implements DomainEvent {

	public PaymentInitiatedEvent(PaymentId paymentId, IdempotencyKey idempotencyKey,
	                             String sourceAccountId, String targetAccountId,
	                             Money amount, PaymentType type, String initiatedBy,
	                             Instant occurredAt) {
		this(UUID.randomUUID(), occurredAt, paymentId, idempotencyKey,
				sourceAccountId, targetAccountId, amount, type, initiatedBy);
	}

	@Override
	public String aggregateId() {
		return paymentId.toString();
	}

	@Override
	public String eventType() {
		return "payment.initiated";
	}
}