package com.matcodem.fincore.payment.infrastructure.messaging.avro;

import java.time.Instant;

import org.apache.avro.specific.SpecificRecord;
import org.springframework.stereotype.Component;

import com.matcodem.fincore.payment.avro.FailureCategory;
import com.matcodem.fincore.payment.domain.event.DomainEvent;
import com.matcodem.fincore.payment.domain.event.PaymentCancelledEvent;
import com.matcodem.fincore.payment.domain.event.PaymentCompletedEvent;
import com.matcodem.fincore.payment.domain.event.PaymentFailedEvent;
import com.matcodem.fincore.payment.domain.event.PaymentFraudRejectedEvent;
import com.matcodem.fincore.payment.domain.event.PaymentInitiatedEvent;
import com.matcodem.fincore.payment.domain.model.Currency;
import com.matcodem.fincore.payment.domain.model.PaymentType;

@Component
public class AvroEventMapper {

	public SpecificRecord toAvro(DomainEvent event) {
		return switch (event) {
			case PaymentInitiatedEvent e -> toAvro(e);
			case PaymentCompletedEvent e -> toAvro(e);
			case PaymentFailedEvent e -> toAvro(e);
			case PaymentFraudRejectedEvent e -> toAvro(e);
			case PaymentCancelledEvent e -> toAvro(e);
			default -> null;
		};
	}

	public com.matcodem.fincore.payment.avro.PaymentInitiatedEvent toAvro(PaymentInitiatedEvent e) {
		return com.matcodem.fincore.payment.avro.PaymentInitiatedEvent.newBuilder()
				.setEventId(e.eventId().toString())
				.setOccurredAt(e.occurredAt())
				.setPaymentId(e.paymentId().toString())
				.setIdempotencyKey(e.idempotencyKey().value())
				.setSourceAccountId(e.sourceAccountId())
				.setTargetAccountId(e.targetAccountId())
				.setAmount(e.amount().getAmount())
				.setCurrency(toCurrency(e.amount().getCurrency()))
				.setPaymentType(toPaymentType(e.type()))
				.setInitiatedBy(e.initiatedBy())
				.setSchemaVersion(1)
				.build();
	}

	public com.matcodem.fincore.payment.avro.PaymentCompletedEvent toAvro(PaymentCompletedEvent e) {
		return com.matcodem.fincore.payment.avro.PaymentCompletedEvent.newBuilder()
				.setEventId(e.eventId().toString())
				.setOccurredAt(e.occurredAt())
				.setPaymentId(e.paymentId().toString())
				.setSourceAccountId(e.sourceAccountId())
				.setTargetAccountId(e.targetAccountId())
				.setAmount(e.amount().getAmount())
				.setCurrency(toCurrency(e.amount().getCurrency()))
				.setPaymentType(toPaymentType(e.type()))
				.setProcessingDurationMs(null) // populated in OutboxEventPublisherImpl if available
				.setSchemaVersion(1)
				.build();
	}

	public com.matcodem.fincore.payment.avro.PaymentFailedEvent toAvro(PaymentFailedEvent e) {
		return com.matcodem.fincore.payment.avro.PaymentFailedEvent.newBuilder()
				.setEventId(e.eventId().toString())
				.setOccurredAt(e.occurredAt())
				.setPaymentId(e.paymentId().toString())
				.setSourceAccountId(e.sourceAccountId())
				.setAmount(e.amount().getAmount())
				.setCurrency(toCurrency(e.amount().getCurrency()))
				.setFailureReason(e.reason())
				.setFailureCategory(classifyFailure(e.reason()))
				.setSchemaVersion(1)
				.build();
	}

	public com.matcodem.fincore.payment.avro.PaymentFraudRejectedEvent toAvro(PaymentFraudRejectedEvent e) {
		return com.matcodem.fincore.payment.avro.PaymentFraudRejectedEvent.newBuilder()
				.setEventId(e.eventId().toString())
				.setOccurredAt(e.occurredAt())
				.setPaymentId(e.paymentId().toString())
				.setSourceAccountId(e.sourceAccountId())
				.setAmount(e.amount().getAmount())
				.setCurrency(toCurrency(e.amount().getCurrency()))
				.setFraudReason(e.reason())
				.setFraudScore(null) // score not available in domain event - populated by fraud service
				.setSchemaVersion(1)
				.build();
	}

	public com.matcodem.fincore.payment.avro.PaymentCancelledEvent toAvro(PaymentCancelledEvent e) {
		return com.matcodem.fincore.payment.avro.PaymentCancelledEvent.newBuilder()
				.setEventId(e.eventId().toString())
				.setOccurredAt(e.occurredAt())
				.setPaymentId(e.paymentId().toString())
				.setSourceAccountId(e.sourceAccountId())
				.setAmount(e.amount().getAmount())
				.setCurrency(toCurrency(e.amount().getCurrency()))
				.setCancellationReason(e.reason())
				.setSchemaVersion(1)
				.build();
	}

	/**
	 * Extracts paymentId from any fraud event.
	 * Payment Service only needs the paymentId to drive its own lifecycle -
	 * it doesn't need the full fraud event structure.
	 */
	public String extractPaymentId(com.matcodem.fincore.fraud.avro.FraudCaseApprovedEvent e) {
		return e.getPaymentId();
	}

	public String extractPaymentId(com.matcodem.fincore.fraud.avro.FraudCaseBlockedEvent e) {
		return e.getPaymentId();
	}

	public String extractPaymentId(com.matcodem.fincore.fraud.avro.FraudCaseEscalatedEvent e) {
		return e.getPaymentId();
	}

	public String extractPaymentId(com.matcodem.fincore.fraud.avro.FraudConfirmedEvent e) {
		return e.getPaymentId();
	}

	public String extractReason(com.matcodem.fincore.fraud.avro.FraudCaseBlockedEvent e) {
		return "%s (score=%d): %s".formatted(e.getTriggeringRule(), e.getFraudScore(), e.getReason());
	}

	public String extractNotes(com.matcodem.fincore.fraud.avro.FraudConfirmedEvent e) {
		return e.getNotes();
	}

	public boolean isReversalRequired(com.matcodem.fincore.fraud.avro.FraudConfirmedEvent e) {
		return e.getReversalRequired();
	}

	private long toEpochMilli(Instant instant) {
		return instant.toEpochMilli();
	}

	private com.matcodem.fincore.payment.avro.Currency toCurrency(Currency c) {
		return com.matcodem.fincore.payment.avro.Currency.valueOf(c.getCode());
	}

	private com.matcodem.fincore.payment.avro.PaymentType toPaymentType(PaymentType t) {
		return com.matcodem.fincore.payment.avro.PaymentType.valueOf(t.name());
	}

	/**
	 * Heuristic classification of failure reasons into machine-readable categories.
	 * Consumers (Notification Service, Analytics) use this to route alerts
	 * and build dashboards without parsing the free-text failureReason.
	 */
	private FailureCategory classifyFailure(String reason) {
		if (reason == null) return FailureCategory.UNKNOWN;
		String lower = reason.toLowerCase();
		if (lower.contains("account service")) return FailureCategory.ACCOUNT_SERVICE_UNAVAILABLE;
		if (lower.contains("fx")) return FailureCategory.FX_SERVICE_UNAVAILABLE;
		if (lower.contains("insufficient")) return FailureCategory.INSUFFICIENT_FUNDS;
		if (lower.contains("frozen")) return FailureCategory.ACCOUNT_FROZEN;
		return FailureCategory.UNKNOWN;
	}
}