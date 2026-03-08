package com.matcodem.fincore.fraud.infrastructure.messaging.avro;

import org.apache.avro.specific.SpecificRecord;
import org.springframework.stereotype.Component;

import com.matcodem.fincore.fraud.domain.event.DomainEvent;
import com.matcodem.fincore.fraud.domain.event.FraudCaseApprovedEvent;
import com.matcodem.fincore.fraud.domain.event.FraudCaseBlockedEvent;
import com.matcodem.fincore.fraud.domain.event.FraudCaseEscalatedEvent;
import com.matcodem.fincore.fraud.domain.event.FraudConfirmedEvent;
import com.matcodem.fincore.fraud.domain.model.PaymentContext;

@Component
public class AvroFraudEventMapper {

	public PaymentContext toPaymentContext(com.matcodem.fincore.fraud.avro.PaymentInitiatedEvent avro) {
		return PaymentContext.builder()
				.paymentId(avro.getPaymentId())
				.idempotencyKey(avro.getIdempotencyKey())
				.sourceAccountId(avro.getSourceAccountId())
				.targetAccountId(avro.getTargetAccountId())
				.amount(avro.getAmount())          // BigDecimal from decimal logical type
				.currency(avro.getCurrency().name())
				.paymentType(avro.getPaymentType().name())
				.initiatedBy(avro.getInitiatedBy())
				.initiatedAt(avro.getOccurredAt())
				.build();
	}

	public SpecificRecord toAvro(DomainEvent event) {
		return switch (event) {
			case FraudCaseApprovedEvent e -> toAvro(e, 0);  // rulesEvaluated unknown here - see note
			case FraudCaseBlockedEvent e -> toAvro(e);
			case FraudCaseEscalatedEvent e -> toAvro(e);
			case FraudConfirmedEvent e -> toAvro(e);
			default -> throw new IllegalStateException(
					"No Avro mapping for fraud event type: " + event.eventType());
		};
	}

	/**
	 * Primary method for FraudCaseApprovedEvent - caller provides rulesEvaluated
	 * from FraudCase aggregate (not available in the event itself).
	 */
	public com.matcodem.fincore.fraud.avro.FraudCaseApprovedEvent toAvro(FraudCaseApprovedEvent e,
	                                                                     int rulesEvaluated) {
		return com.matcodem.fincore.fraud.avro.FraudCaseApprovedEvent.newBuilder()
				.setEventId(e.eventId().toString())
				.setOccurredAt(e.occurredAt())
				.setFraudCaseId(e.fraudCaseId().toString())
				.setPaymentId(e.paymentId())
				.setFraudScore(e.score().getValue())
				.setRulesEvaluated(rulesEvaluated)
				.setReviewedBy(null)  // auto-approved
				.setSchemaVersion(1)
				.build();
	}

	public com.matcodem.fincore.fraud.avro.FraudCaseBlockedEvent toAvro(FraudCaseBlockedEvent e) {
		return com.matcodem.fincore.fraud.avro.FraudCaseBlockedEvent.newBuilder()
				.setEventId(e.eventId().toString())
				.setOccurredAt(e.occurredAt())
				.setFraudCaseId(e.fraudCaseId().toString())
				.setPaymentId(e.paymentId())
				.setFraudScore(e.score().getValue())
				.setTriggeringRule(extractTriggeringRule(e.reason()))
				.setReason(e.reason())
				.setRuleScores(java.util.Map.of())  // populated in v2 when ruleResults available
				.setSchemaVersion(1)
				.build();
	}

	public com.matcodem.fincore.fraud.avro.FraudCaseEscalatedEvent toAvro(FraudCaseEscalatedEvent e) {
		return com.matcodem.fincore.fraud.avro.FraudCaseEscalatedEvent.newBuilder()
				.setEventId(e.eventId().toString())
				.setOccurredAt(e.occurredAt())
				.setFraudCaseId(e.fraudCaseId().toString())
				.setPaymentId(e.paymentId())
				.setFraudScore(e.score().getValue())
				.setEscalationReason("Score %d - %s".formatted(e.score().getValue(), e.score().getLevel().name()))
				.setAssignedAnalyst(null)
				.setSchemaVersion(1)
				.build();
	}

	public com.matcodem.fincore.fraud.avro.FraudConfirmedEvent toAvro(FraudConfirmedEvent e) {
		return com.matcodem.fincore.fraud.avro.FraudConfirmedEvent.newBuilder()
				.setEventId(e.eventId().toString())
				.setOccurredAt(e.occurredAt())
				.setFraudCaseId(e.fraudCaseId().toString())
				.setPaymentId(e.paymentId())
				.setConfirmedBy(e.sourceAccountId()) // confirmedBy not in current domain event - see comment
				.setNotes(e.notes())
				.setReversalRequired(true)
				.setSchemaVersion(1)
				.build();
	}

	/**
	 * Extracts rule name from reason string like "VelocityRule: score=85 (threshold=70)".
	 * Returns "UNKNOWN" if format doesn't match - safe fallback, never throws.
	 */
	private String extractTriggeringRule(String reason) {
		if (reason == null) return "UNKNOWN";
		int colonIdx = reason.indexOf(':');
		return colonIdx > 0 ? reason.substring(0, colonIdx).trim() : "UNKNOWN";
	}
}