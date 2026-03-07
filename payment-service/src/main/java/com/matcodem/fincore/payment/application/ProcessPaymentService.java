package com.matcodem.fincore.payment.application;

import java.util.NoSuchElementException;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.matcodem.fincore.payment.adapter.out.client.AccountServiceWebClient.AccountServiceUnavailableException;
import com.matcodem.fincore.payment.adapter.out.client.FxServiceWebClient;
import com.matcodem.fincore.payment.adapter.out.client.FxServiceWebClient.FxServiceUnavailableException;
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
 * ── processPayment() — the critical happy path ────────────────────────────────
 * <p>
 * Called after fraud.case.approved event is received from Fraud Detection Service.
 * <p>
 * Steps:
 * 1. Load payment, verify PENDING (idempotency guard)
 * 2. Acquire distributed lock on source + target accounts (Redisson MultiLock)
 * → sorted lock order prevents deadlocks across concurrent payments
 * 3. Re-fetch inside lock to catch race conditions
 * 4. [FX] If FX_CONVERSION: call FX Service synchronously — rate locked, conversion persisted
 * 5. Debit source account via Account Service
 * 6. Credit target account via Account Service
 * 7. Mark COMPLETED, save, publish via outbox
 * <p>
 * ── Failure compensation ───────────────────────────────────────────────────────
 * <p>
 * If debit succeeds but credit fails:
 * → We mark payment FAILED and log at CRITICAL level
 * → A human (ops) must reconcile manually or an async compensation job handles it
 * → We do NOT attempt re-credit here — that call may also fail and leave us
 * in a deeper inconsistent state
 * → In production: PagerDuty alert + automatic JIRA ticket creation
 * <p>
 * If FX conversion fails:
 * → No money has moved — mark FAILED, no compensation needed
 * <p>
 * ── initiateReversalIfNeeded() ─────────────────────────────────────────────────
 * <p>
 * Called after fraud.confirmed (compliance confirmed a completed payment was fraudulent).
 * If payment is COMPLETED: reverse the money movement (debit target, credit source).
 * If payment is PENDING/PROCESSING: reject as fraudulent (no money moved yet).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessPaymentService implements ProcessPaymentUseCase {

	private final PaymentRepository paymentRepository;
	private final AccountServiceClient accountServiceClient;
	private final FxServiceWebClient fxServiceClient;
	private final OutboxEventPublisher outboxEventPublisher;
	private final PaymentLockService lockService;
	private final MeterRegistry meterRegistry;

	@Override
	@Timed(value = "payment.process.duration")
	public void processPayment(String paymentId) {
		log.info("Processing payment: {}", paymentId);

		Payment payment = loadPayment(paymentId);

		if (!payment.isPending()) {
			log.warn("Payment {} is not PENDING ({}), skipping", paymentId, payment.getStatus());
			return;
		}

		lockService.executeWithLock(
				payment.getSourceAccountId(),
				payment.getTargetAccountId(),
				() -> executeUnderLock(paymentId)
		);
	}

	/**
	 * Separated from processPayment() for clarity — this is the work done
	 * while holding the distributed lock.
	 */
	private void executeUnderLock(String paymentId) {
		// Re-fetch: another instance may have claimed it between our check and lock acquisition
		Payment payment = loadPayment(paymentId);
		if (!payment.isPending()) {
			log.warn("Payment {} already claimed by another instance ({}), skipping",
					paymentId, payment.getStatus());
			return;
		}

		payment.startProcessing();

		try {
			if (payment.getType() == PaymentType.FX_CONVERSION) {
				performFxConversion(payment);
			}

			accountServiceClient.debitAccount(
					payment.getSourceAccountId(), payment.getAmount(), paymentId);

			accountServiceClient.creditAccount(
					payment.getTargetAccountId(), payment.getAmount(), paymentId);

			payment.complete();
			saveWithOutbox(payment);

			meterRegistry.counter("payment.completed", "type", payment.getType().name()).increment();
			log.info("Payment {} COMPLETED", paymentId);

		} catch (FxServiceUnavailableException ex) {
			// FX failed before any money moved — safe to fail
			log.error("FX conversion failed for payment {}: {}", paymentId, ex.getMessage());
			payment.fail("FX conversion unavailable: " + ex.getMessage());
			saveWithOutbox(payment);
			meterRegistry.counter("payment.failed", "reason", "fx_service").increment();

		} catch (AccountServiceUnavailableException ex) {
			// Debit or credit failed — may be a partial failure (see class Javadoc)
			log.error("Account Service failed for payment {}: {}", paymentId, ex.getMessage());
			payment.fail("Account Service unavailable: " + ex.getMessage());
			saveWithOutbox(payment);
			meterRegistry.counter("payment.failed", "reason", "account_service").increment();
		}
	}

	@Override
	@Transactional
	public void rejectForFraud(String paymentId, String reason) {
		log.warn("Fraud rejection for payment {}: {}", paymentId, reason);
		Payment payment = loadPayment(paymentId);
		payment.rejectAsFraudulent(reason);
		saveWithOutbox(payment);
		meterRegistry.counter("payment.fraud.rejected").increment();
	}

	@Override
	@Transactional
	public void failPayment(String paymentId, String reason) {
		log.error("Failing payment {}: {}", paymentId, reason);
		Payment payment = loadPayment(paymentId);
		if (payment.isPending() || payment.isProcessing()) {
			payment.fail(reason);
			saveWithOutbox(payment);
		} else {
			log.warn("Payment {} in non-faileable state {}, skipping", paymentId, payment.getStatus());
		}
	}

	@Override
	@Transactional
	public void initiateReversalIfNeeded(String paymentId, String reason) {
		Payment payment = loadPayment(paymentId);

		switch (payment.getStatus()) {
			case COMPLETED -> reverseCompletedPayment(payment, reason);
			case PENDING, PROCESSING -> rejectForFraud(paymentId, "Fraud confirmed: " + reason);
			default -> log.info("Payment {} already in terminal state {}, no action needed",
					paymentId, payment.getStatus());
		}
	}

	private void reverseCompletedPayment(Payment payment, String reason) {
		String paymentId = payment.getId().value().toString();
		log.error("REVERSAL NEEDED for payment {} — fraud confirmed: {}", paymentId, reason);

		try {
			accountServiceClient.debitAccount(
					payment.getTargetAccountId(), payment.getAmount(), "REVERSAL-" + paymentId);
			accountServiceClient.creditAccount(
					payment.getSourceAccountId(), payment.getAmount(), "REVERSAL-" + paymentId);

			log.info("Reversal completed for payment {}", paymentId);
			meterRegistry.counter("payment.reversal.completed").increment();

		} catch (Exception ex) {
			// CRITICAL: money already moved, reversal failed — ops must intervene
			log.error("CRITICAL: Reversal FAILED for payment {} — MANUAL INTERVENTION REQUIRED: {}",
					paymentId, ex.getMessage());
			meterRegistry.counter("payment.reversal.failed").increment();
			// TODO: trigger PagerDuty alert + auto-create JIRA ticket
		}
	}

	private void performFxConversion(Payment payment) {
		String paymentId = payment.getId().value().toString();
		Money money = payment.getAmount();
		String pair = money.getCurrency().getCode() + "PLN";

		FxServiceWebClient.FxConversionResult fx = fxServiceClient.convert(
				paymentId,
				payment.getSourceAccountId(),
				payment.getInitiatedBy(),
				pair,
				money.getAmount(),
				"BUY_BASE"
		);

		log.info("FX locked for payment {} — {} → {} PLN at {} (fee: {}, conversionId: {})",
				paymentId, money.getAmount(), fx.convertedAmount(),
				fx.appliedRate(), fx.fee(), fx.conversionId());
	}

	@Transactional
	protected void saveWithOutbox(Payment payment) {
		paymentRepository.save(payment);
		payment.pullDomainEvents()
				.forEach(event -> outboxEventPublisher.publish(event, "Payment"));
	}

	private Payment loadPayment(String paymentId) {
		PaymentId id = PaymentId.of(UUID.fromString(paymentId));
		return paymentRepository.findById(id)
				.orElseThrow(() -> new NoSuchElementException("Payment not found: " + paymentId));
	}
}