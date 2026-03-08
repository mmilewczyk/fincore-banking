package com.matcodem.fincore.payment.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.matcodem.fincore.payment.domain.event.DomainEvent;
import com.matcodem.fincore.payment.domain.event.PaymentCancelledEvent;
import com.matcodem.fincore.payment.domain.event.PaymentCompletedEvent;
import com.matcodem.fincore.payment.domain.event.PaymentFailedEvent;
import com.matcodem.fincore.payment.domain.event.PaymentFraudRejectedEvent;
import com.matcodem.fincore.payment.domain.event.PaymentInitiatedEvent;

/**
 * Payment Aggregate Root.
 * <p>
 * Lifecycle: PENDING → PROCESSING → COMPLETED | FAILED | CANCELLED
 * <p>
 * Key invariants enforced here:
 * - A payment can only be processed once (status guard)
 * - Amount must be positive
 * - Source and target accounts must differ
 * - Cancellation only allowed before processing begins
 */
public class Payment {

	private final PaymentId id;
	private final IdempotencyKey idempotencyKey;
	private final String sourceAccountId;
	private final String targetAccountId;
	private final Money amount;
	private final PaymentType type;
	private PaymentStatus status;
	private String failureReason;
	private final String initiatedBy;      // userId from JWT
	private final Instant createdAt;
	private Instant updatedAt;
	private long version;

	private final List<DomainEvent> domainEvents = new ArrayList<>();

	private Payment(PaymentId id, IdempotencyKey idempotencyKey,
	                String sourceAccountId, String targetAccountId,
	                Money amount, PaymentType type, String initiatedBy,
	                PaymentStatus status, Instant createdAt, long version) {
		this.id = id;
		this.idempotencyKey = idempotencyKey;
		this.sourceAccountId = sourceAccountId;
		this.targetAccountId = targetAccountId;
		this.amount = amount;
		this.type = type;
		this.status = status;
		this.initiatedBy = initiatedBy;
		this.createdAt = createdAt;
		this.updatedAt = createdAt;
		this.version = version;
	}

	/**
	 * Initiates a new payment — validates invariants and records event.
	 */
	public static Payment initiate(
			IdempotencyKey idempotencyKey,
			String sourceAccountId,
			String targetAccountId,
			Money amount,
			PaymentType type,
			String initiatedBy) {

		Objects.requireNonNull(idempotencyKey, "IdempotencyKey required");
		Objects.requireNonNull(sourceAccountId, "Source account required");
		Objects.requireNonNull(targetAccountId, "Target account required");
		Objects.requireNonNull(amount, "Amount required");
		Objects.requireNonNull(type, "Payment type required");
		Objects.requireNonNull(initiatedBy, "InitiatedBy required");

		if (!amount.isPositive()) {
			throw new IllegalArgumentException("Payment amount must be positive: " + amount);
		}
		if (sourceAccountId.equals(targetAccountId)) {
			throw new IllegalArgumentException("Source and target accounts must differ");
		}

		PaymentId id = PaymentId.generate();
		Instant now = Instant.now();

		Payment payment = new Payment(
				id, idempotencyKey, sourceAccountId, targetAccountId,
				amount, type, initiatedBy, PaymentStatus.PENDING, now, 0L
		);

		payment.recordEvent(new PaymentInitiatedEvent(
				id, idempotencyKey, sourceAccountId, targetAccountId, amount, type, initiatedBy, now
		));

		return payment;
	}

	/**
	 * Reconstitution from persistence — no events recorded.
	 */
	public static Payment reconstitute(
			PaymentId id, IdempotencyKey idempotencyKey,
			String sourceAccountId, String targetAccountId,
			Money amount, PaymentType type, String initiatedBy,
			PaymentStatus status, String failureReason,
			Instant createdAt, Instant updatedAt, long version) {

		Payment payment = new Payment(
				id, idempotencyKey, sourceAccountId, targetAccountId,
				amount, type, initiatedBy, status, createdAt, version
		);
		payment.failureReason = failureReason;
		payment.updatedAt = updatedAt;
		return payment;
	}

	/**
	 * Marks payment as being processed — called when distributed lock is acquired.
	 */
	public void startProcessing() {
		if (status != PaymentStatus.PENDING) {
			throw new IllegalStateException(
					"Cannot start processing payment in status: %s (id: %s)".formatted(status, id)
			);
		}
		this.status = PaymentStatus.PROCESSING;
		this.updatedAt = Instant.now();
	}

	/**
	 * Marks payment as successfully completed.
	 * Called after both accounts are updated and events published.
	 */
	public void complete() {
		if (status != PaymentStatus.PROCESSING) {
			throw new IllegalStateException(
					"Cannot complete payment in status: %s (id: %s)".formatted(status, id)
			);
		}
		this.status = PaymentStatus.COMPLETED;
		this.updatedAt = Instant.now();
		recordEvent(new PaymentCompletedEvent(id, sourceAccountId, targetAccountId, amount, updatedAt, type));
	}

	/**
	 * Marks payment as failed with a reason.
	 * Can be called from PENDING or PROCESSING state.
	 */
	public void fail(String reason) {
		if (status == PaymentStatus.COMPLETED || status == PaymentStatus.CANCELLED) {
			throw new IllegalStateException(
					"Cannot fail payment in terminal status: %s (id: %s)".formatted(status, id)
			);
		}
		Objects.requireNonNull(reason, "Failure reason required");

		this.status = PaymentStatus.FAILED;
		this.failureReason = reason;
		this.updatedAt = Instant.now();
		recordEvent(new PaymentFailedEvent(id, sourceAccountId, amount, reason, updatedAt));
	}

	/**
	 * Cancels a pending payment before it starts processing.
	 */
	public void cancel(String reason) {
		if (status != PaymentStatus.PENDING) {
			throw new IllegalStateException(
					"Cannot cancel payment in status: %s — only PENDING payments can be cancelled".formatted(status)
			);
		}
		this.status = PaymentStatus.CANCELLED;
		this.failureReason = reason;
		this.updatedAt = Instant.now();
		recordEvent(new PaymentCancelledEvent(id, sourceAccountId, amount, reason, updatedAt));
	}

	/**
	 * Marks payment as rejected by fraud detection.
	 */
	public void rejectAsFraudulent(String reason) {
		if (status != PaymentStatus.PENDING && status != PaymentStatus.PROCESSING) {
			throw new IllegalStateException(
					"Cannot reject payment in status: %s".formatted(status)
			);
		}
		this.status = PaymentStatus.REJECTED_FRAUD;
		this.failureReason = reason;
		this.updatedAt = Instant.now();
		recordEvent(new PaymentFraudRejectedEvent(id, sourceAccountId, amount, reason, updatedAt));
	}

	private void recordEvent(DomainEvent event) {
		domainEvents.add(event);
	}

	public List<DomainEvent> pullDomainEvents() {
		List<DomainEvent> events = new ArrayList<>(domainEvents);
		domainEvents.clear();
		return Collections.unmodifiableList(events);
	}

	public boolean isPending() {
		return status == PaymentStatus.PENDING;
	}

	public boolean isProcessing() {
		return status == PaymentStatus.PROCESSING;
	}

	public boolean isCompleted() {
		return status == PaymentStatus.COMPLETED;
	}

	public boolean isFailed() {
		return status == PaymentStatus.FAILED;
	}

	public boolean isTerminal() {
		return status == PaymentStatus.COMPLETED
				|| status == PaymentStatus.FAILED
				|| status == PaymentStatus.CANCELLED
				|| status == PaymentStatus.REJECTED_FRAUD;
	}

	public PaymentId getId() {
		return id;
	}

	public IdempotencyKey getIdempotencyKey() {
		return idempotencyKey;
	}

	public String getSourceAccountId() {
		return sourceAccountId;
	}

	public String getTargetAccountId() {
		return targetAccountId;
	}

	public Money getAmount() {
		return amount;
	}

	public PaymentType getType() {
		return type;
	}

	public PaymentStatus getStatus() {
		return status;
	}

	public String getFailureReason() {
		return failureReason;
	}

	public String getInitiatedBy() {
		return initiatedBy;
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
		if (!(o instanceof Payment p)) return false;
		return Objects.equals(id, p.id);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id);
	}

	@Override
	public String toString() {
		return "Payment{id=%s, status=%s, amount=%s}".formatted(id, status, amount);
	}
}