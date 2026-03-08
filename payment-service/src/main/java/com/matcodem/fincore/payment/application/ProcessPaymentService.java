package com.matcodem.fincore.payment.application;

import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.matcodem.fincore.payment.adapter.out.client.AccountServiceWebClient.AccountServiceUnavailableException;
import com.matcodem.fincore.payment.adapter.out.client.FxServiceWebClient;
import com.matcodem.fincore.payment.adapter.out.client.FxServiceWebClient.FxServiceUnavailableException;
import com.matcodem.fincore.payment.application.processor.PaymentProcessor;
import com.matcodem.fincore.payment.domain.event.DomainEvent;
import com.matcodem.fincore.payment.domain.model.Currency;
import com.matcodem.fincore.payment.domain.model.Money;
import com.matcodem.fincore.payment.domain.model.Payment;
import com.matcodem.fincore.payment.domain.model.PaymentId;
import com.matcodem.fincore.payment.domain.model.PaymentType;
import com.matcodem.fincore.payment.domain.port.in.ProcessPaymentUseCase;
import com.matcodem.fincore.payment.domain.port.out.AccountServiceClient;
import com.matcodem.fincore.payment.domain.port.out.OutboxEventPublisher;
import com.matcodem.fincore.payment.domain.port.out.PaymentLockService;
import com.matcodem.fincore.payment.domain.port.out.PaymentRepository;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Handles payment processing, fraud rejection, and fraud-confirmed reversals.
 * <p>
 * ── TRANSACTION STRATEGY ──────────────────────────────────────────────────────
 * <p>
 * processPayment() is intentionally NOT @Transactional at the top level.
 * The distributed lock (Redisson MultiLock) must be acquired BEFORE opening
 * a DB transaction, otherwise the transaction can be held open while waiting
 * for the lock - exhausting the connection pool under contention.
 * <p>
 * Transaction boundary is inside executeUnderLock(), which opens a new
 *
 * @Transactional scope after the lock is acquired. This ensures:
 * - Lock held for the minimum necessary duration
 * - DB transaction open only during actual work (re-fetch, modify, save)
 * - If the transaction rolls back, the lock is still released in finally
 * <p>
 * ── PROCESSING STEPS ─────────────────────────────────────────────────────────
 * <p>
 * 1. Load payment, verify PENDING (pre-lock optimistic check)
 * 2. Acquire Redisson MultiLock on [sourceAccountId, targetAccountId]
 * - sorted alphabetically to prevent deadlock
 * 3. Re-fetch inside lock + transaction (eliminates race condition window)
 * 4. startProcessing() - status PENDING -> PROCESSING
 * 5. [FX_CONVERSION only] FX Service call - synchronous, rate locked
 * 6. Account Service debit - source account
 * 7. Account Service credit - target account
 * 8. complete() - status PROCESSING -> COMPLETED
 * 9. save() + publish domain events to outbox (same transaction)
 * <p>
 * ── PARTIAL FAILURE / COMPENSATION ───────────────────────────────────────────
 * <p>
 * If debit OK but credit FAILS:
 * The payment is marked FAILED. Money is debited but not credited.
 * This is an inconsistency that CANNOT be resolved here automatically
 * (a retry credit might also fail, worsening the state).
 * -> CRITICAL log emitted - ops alerted via Prometheus alert rule
 * -> Payment stored as FAILED with full reason for audit trail
 * -> Compensation job or manual ops intervention required
 * In production: PagerDuty + auto JIRA ticket.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessPaymentService implements ProcessPaymentUseCase {

	private final PaymentRepository paymentRepository;
	private final AccountServiceClient accountServiceClient;
	private final OutboxEventPublisher outboxEventPublisher;
	private final PaymentLockService lockService;
	private final MeterRegistry meterRegistry;
	private final PaymentProcessor paymentProcessor;

	@Override
	@Timed(value = "payment.process.duration")
	public void processPayment(String paymentId) {
		log.info("Processing payment: {}", paymentId);

		// Pre-lock check: avoids acquiring a lock for an already-terminal payment.
		// This is an optimistic read - re-checked under lock before any mutation.
		Payment payment = loadPayment(paymentId);
		if (!payment.isPending()) {
			log.warn("Payment {} is not PENDING ({}) - skipping", paymentId, payment.getStatus());
			return;
		}

		lockService.executeWithLock(
				payment.getSourceAccountId(),
				payment.getTargetAccountId(),
				() -> paymentProcessor.executeUnderLock(paymentId)
		);
	}

	@Override
	@Transactional
	public void rejectForFraud(String paymentId, String reason) {
		log.warn("Fraud rejection for payment {}: {}", paymentId, reason);
		Payment payment = loadPayment(paymentId);
		payment.rejectAsFraudulent(reason);
		saveAndPublish(payment);
		meterRegistry.counter("payment.fraud.rejected").increment();
	}

	@Override
	@Transactional
	public void failPayment(String paymentId, String reason) {
		log.error("Failing payment {}: {}", paymentId, reason);
		Payment payment = loadPayment(paymentId);
		if (payment.isPending() || payment.isProcessing()) {
			payment.fail(reason);
			saveAndPublish(payment);
		} else {
			log.warn("Payment {} in non-failable state {} - skipping", paymentId, payment.getStatus());
		}
	}

	@Override
	@Transactional
	public void initiateReversalIfNeeded(String paymentId, String reason) {
		Payment payment = loadPayment(paymentId);
		switch (payment.getStatus()) {
			case COMPLETED -> reverseCompletedPayment(payment, reason);
			case PENDING, PROCESSING -> rejectForFraud(paymentId, "Fraud confirmed: " + reason);
			default -> log.info("Payment {} already terminal ({}) - no action",
					paymentId, payment.getStatus());
		}
	}

	private void reverseCompletedPayment(Payment payment, String reason) {
		String paymentId = payment.getId().toString();
		log.error("REVERSAL REQUIRED for payment {} - fraud confirmed: {}", paymentId, reason);

		try {
			// Reverse: debit the recipient, credit the sender
			String reversalRef = "REVERSAL-" + paymentId;
			accountServiceClient.debitAccount(
					payment.getTargetAccountId(), payment.getAmount(), reversalRef);
			accountServiceClient.creditAccount(
					payment.getSourceAccountId(), payment.getAmount(), reversalRef);

			log.info("Reversal COMPLETED for payment {}", paymentId);
			meterRegistry.counter("payment.reversal.completed").increment();

		} catch (Exception ex) {
			// Reversal failed after fraud confirmed - money cannot be automatically recovered.
			// This requires human intervention. DO NOT retry here - could worsen state.
			log.error("CRITICAL: Reversal FAILED for payment {} - MANUAL INTERVENTION REQUIRED. Reason: {}",
					paymentId, ex.getMessage());
			meterRegistry.counter("payment.reversal.failed").increment();
			// TODO: PagerDuty trigger + auto-create JIRA ticket via webhook
		}
	}

	/**
	 * Persist the payment and flush all accumulated domain events to the outbox.
	 * Called within an active @Transactional context - payment row + outbox rows
	 * are written atomically.
	 * <p>
	 * Note: pullDomainEvents() clears the in-memory list - idempotent, safe to call
	 * multiple times (second call returns empty list).
	 */
	private void saveAndPublish(Payment payment) {
		paymentRepository.save(payment);
		List<DomainEvent> events = payment.pullDomainEvents();
		events.forEach(event -> outboxEventPublisher.publish(event, "Payment"));
		log.debug("Saved payment {} ({}) and queued {} domain event(s) to outbox",
				payment.getId(), payment.getStatus(), events.size());
	}

	private Payment loadPayment(String paymentId) {
		return paymentRepository.findById(PaymentId.of(paymentId))
				.orElseThrow(() -> new NoSuchElementException("Payment not found: " + paymentId));
	}
}