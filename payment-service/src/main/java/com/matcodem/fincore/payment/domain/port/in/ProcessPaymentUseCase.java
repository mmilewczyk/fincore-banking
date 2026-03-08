package com.matcodem.fincore.payment.domain.port.in;

import com.matcodem.fincore.payment.domain.model.PaymentId;

/**
 * Driving port - full payment lifecycle driven by external events.
 * <p>
 * Called by:
 * processPayment()           <- FraudDecisionKafkaConsumer (fraud.case.approved)
 * rejectForFraud()           <- FraudDecisionKafkaConsumer (fraud.case.blocked)
 * failPayment()              <- FxConversionResultKafkaConsumer (fx.conversion.failed)
 * initiateReversalIfNeeded() <- FraudDecisionKafkaConsumer (fraud.confirmed)
 */
public interface ProcessPaymentUseCase {

	/**
	 * Main processing flow:
	 * 1. Load payment (must be PENDING)
	 * 2. Acquire distributed lock on both account IDs
	 * 3. If FX_CONVERSION -> call FX Service synchronously (rate lock)
	 * 4. Debit source account via Account Service
	 * 5. Credit target account via Account Service
	 * 6. Mark COMPLETED, publish payment.completed via outbox
	 */
	void processPayment(String paymentId);

	/**
	 * Backwards compat - accepts PaymentId directly
	 */
	default void processPayment(PaymentId paymentId) {
		processPayment(paymentId.value().toString());
	}

	/**
	 * Fraud service blocked this payment - mark REJECTED_FRAUD.
	 * No money movement. Publishes payment.fraud.rejected via outbox.
	 */
	void rejectForFraud(String paymentId, String reason);

	/**
	 * Generic failure (FX unavailable, Account Service down, etc.)
	 * Marks FAILED, publishes payment.failed via outbox.
	 */
	void failPayment(String paymentId, String reason);

	/**
	 * Compliance confirmed fraud after manual review.
	 * If COMPLETED -> triggers reversal (re-debit target, re-credit source).
	 * If PENDING    -> rejects immediately.
	 */
	void initiateReversalIfNeeded(String paymentId, String reason);
}