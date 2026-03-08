package com.matcodem.fincore.fraud.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.matcodem.fincore.fraud.domain.event.DomainEvent;
import com.matcodem.fincore.fraud.domain.event.FraudCaseApprovedEvent;
import com.matcodem.fincore.fraud.domain.event.FraudCaseBlockedEvent;
import com.matcodem.fincore.fraud.domain.event.FraudCaseEscalatedEvent;
import com.matcodem.fincore.fraud.domain.event.FraudConfirmedEvent;

/**
 * FraudCase Aggregate Root.
 * <p>
 * Represents the result of fraud analysis for a single payment.
 * Records which rules fired, the composite score, decision, and resolution.
 * <p>
 * Lifecycle:
 * OPEN -> APPROVED (auto, low risk)
 * -> BLOCKED  (auto, medium/high risk)
 * -> UNDER_REVIEW -> APPROVED | CONFIRMED_FRAUD
 */
public class FraudCase {

	private final FraudCaseId id;
	private final String paymentId;
	private final String sourceAccountId;
	private final String initiatedBy;
	private RiskScore compositeScore;
	private FraudCaseStatus status;
	private final List<RuleResult> ruleResults;
	private String reviewedBy;
	private String reviewNotes;
	private final Instant createdAt;
	private Instant updatedAt;
	private long version;

	private final List<DomainEvent> domainEvents = new ArrayList<>();

	private FraudCase(FraudCaseId id, String paymentId, String sourceAccountId,
	                  String initiatedBy, RiskScore compositeScore, FraudCaseStatus status,
	                  List<RuleResult> ruleResults, Instant createdAt, long version) {
		this.id = id;
		this.paymentId = paymentId;
		this.sourceAccountId = sourceAccountId;
		this.initiatedBy = initiatedBy;
		this.compositeScore = compositeScore;
		this.status = status;
		this.ruleResults = new ArrayList<>(ruleResults);
		this.createdAt = createdAt;
		this.updatedAt = createdAt;
		this.version = version;
	}

	public static FraudCase evaluate(
			String paymentId,
			String sourceAccountId,
			String initiatedBy,
			RiskScore compositeScore,
			List<RuleResult> ruleResults) {

		FraudCaseId id = FraudCaseId.generate();
		Instant now = Instant.now();

		FraudCaseStatus status = determineStatus(compositeScore);

		FraudCase fraudCase = new FraudCase(
				id, paymentId, sourceAccountId, initiatedBy,
				compositeScore, status, ruleResults, now, 0L
		);

		// Record appropriate event based on decision
		switch (status) {
			case APPROVED -> fraudCase.recordEvent(
					new FraudCaseApprovedEvent(id, paymentId, compositeScore, now)
			);
			case BLOCKED -> fraudCase.recordEvent(
					new FraudCaseBlockedEvent(id, paymentId, compositeScore,
							fraudCase.buildBlockReason(), sourceAccountId, now)
			);
			case UNDER_REVIEW -> fraudCase.recordEvent(
					new FraudCaseEscalatedEvent(id, paymentId, compositeScore, now)
			);
		}

		return fraudCase;
	}

	public static FraudCase reconstitute(
			FraudCaseId id, String paymentId, String sourceAccountId, String initiatedBy,
			RiskScore compositeScore, FraudCaseStatus status, List<RuleResult> ruleResults,
			String reviewedBy, String reviewNotes,
			Instant createdAt, Instant updatedAt, long version) {

		FraudCase fc = new FraudCase(id, paymentId, sourceAccountId, initiatedBy,
				compositeScore, status, ruleResults, createdAt, version);
		fc.reviewedBy = reviewedBy;
		fc.reviewNotes = reviewNotes;
		fc.updatedAt = updatedAt;
		return fc;
	}

	/**
	 * Compliance officer manually approves the case after review.
	 */
	public void approveAfterReview(String reviewedBy, String notes) {
		assertUnderReview();
		this.status = FraudCaseStatus.APPROVED;
		this.reviewedBy = Objects.requireNonNull(reviewedBy);
		this.reviewNotes = notes;
		this.updatedAt = Instant.now();
		recordEvent(new FraudCaseApprovedEvent(id, paymentId, compositeScore, updatedAt));
	}

	/**
	 * Compliance officer confirms this is genuine fraud.
	 */
	public void confirmFraud(String reviewedBy, String notes) {
		assertUnderReview();
		this.status = FraudCaseStatus.CONFIRMED_FRAUD;
		this.reviewedBy = Objects.requireNonNull(reviewedBy);
		this.reviewNotes = Objects.requireNonNull(notes);
		this.updatedAt = Instant.now();
		recordEvent(new FraudConfirmedEvent(id, paymentId, sourceAccountId, compositeScore, notes, updatedAt));
	}

	/**
	 * Automatically escalate to manual review (score in MEDIUM band).
	 */
	public void escalateToReview() {
		if (status != FraudCaseStatus.BLOCKED) {
			throw new IllegalStateException("Only BLOCKED cases can be escalated to review");
		}
		this.status = FraudCaseStatus.UNDER_REVIEW;
		this.updatedAt = Instant.now();
		recordEvent(new FraudCaseEscalatedEvent(id, paymentId, compositeScore, updatedAt));
	}

	private static FraudCaseStatus determineStatus(RiskScore score) {
		return switch (score.getLevel()) {
			case LOW -> FraudCaseStatus.APPROVED;
			case MEDIUM -> FraudCaseStatus.UNDER_REVIEW;
			case HIGH,
			     CRITICAL -> FraudCaseStatus.BLOCKED;
		};
	}

	private String buildBlockReason() {
		return ruleResults.stream()
				.filter(r -> r.score().getValue() > 0)
				.map(r -> "%s (+%d)".formatted(r.ruleName(), r.score().getValue()))
				.reduce((a, b) -> a + ", " + b)
				.orElse("Unknown");
	}

	private void assertUnderReview() {
		if (status != FraudCaseStatus.UNDER_REVIEW) {
			throw new IllegalStateException(
					"Case %s is not UNDER_REVIEW (current: %s)".formatted(id, status)
			);
		}
	}

	private void recordEvent(DomainEvent event) {
		domainEvents.add(event);
	}

	public List<DomainEvent> pullDomainEvents() {
		List<DomainEvent> events = new ArrayList<>(domainEvents);
		domainEvents.clear();
		return Collections.unmodifiableList(events);
	}

	public boolean isBlocked() {
		return status == FraudCaseStatus.BLOCKED;
	}

	public boolean isApproved() {
		return status == FraudCaseStatus.APPROVED;
	}

	public boolean isUnderReview() {
		return status == FraudCaseStatus.UNDER_REVIEW;
	}

	public boolean requiresAccountFreeze() {
		return compositeScore.requiresAccountFreeze();
	}

	public FraudCaseId getId() {
		return id;
	}

	public String getPaymentId() {
		return paymentId;
	}

	public String getSourceAccountId() {
		return sourceAccountId;
	}

	public String getInitiatedBy() {
		return initiatedBy;
	}

	public RiskScore getCompositeScore() {
		return compositeScore;
	}

	public FraudCaseStatus getStatus() {
		return status;
	}

	public List<RuleResult> getRuleResults() {
		return Collections.unmodifiableList(ruleResults);
	}

	public String getReviewedBy() {
		return reviewedBy;
	}

	public String getReviewNotes() {
		return reviewNotes;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public long getVersion() {
		return version;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof FraudCase fc)) return false;
		return Objects.equals(id, fc.id);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id);
	}

	@Override
	public String toString() {
		return "FraudCase{id=%s, payment=%s, score=%s, status=%s}"
				.formatted(id, paymentId, compositeScore, status);
	}
}
