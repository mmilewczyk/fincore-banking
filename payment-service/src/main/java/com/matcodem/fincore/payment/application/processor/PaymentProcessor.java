package com.matcodem.fincore.payment.application.processor;

import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.matcodem.fincore.payment.adapter.out.client.AccountServiceWebClient;
import com.matcodem.fincore.payment.adapter.out.client.FxServiceWebClient;
import com.matcodem.fincore.payment.domain.event.DomainEvent;
import com.matcodem.fincore.payment.domain.model.Currency;
import com.matcodem.fincore.payment.domain.model.Money;
import com.matcodem.fincore.payment.domain.model.Payment;
import com.matcodem.fincore.payment.domain.model.PaymentId;
import com.matcodem.fincore.payment.domain.model.PaymentType;
import com.matcodem.fincore.payment.domain.port.out.AccountServiceClient;
import com.matcodem.fincore.payment.domain.port.out.OutboxEventPublisher;
import com.matcodem.fincore.payment.domain.port.out.PaymentRepository;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentProcessor {

	private final PaymentRepository paymentRepository;
	private final AccountServiceClient accountServiceClient;
	private final OutboxEventPublisher outboxEventPublisher;
	private final FxServiceWebClient fxServiceClient;
	private final MeterRegistry meterRegistry;

	/**
	 * Called while the distributed lock is held.
	 * Opens its own @Transactional scope — the lock is acquired BEFORE the
	 * transaction opens, so the DB connection is not held during lock wait.
	 */
	@Transactional
	public void executeUnderLock(String paymentId) {
		// Re-fetch with fresh snapshot — mandatory to avoid acting on stale state
		// from before the lock was acquired (another instance may have raced us).
		Payment payment = loadPayment(paymentId);
		if (!payment.isPending()) {
			log.warn("Payment {} already claimed ({}) — skipping under lock",
					paymentId, payment.getStatus());
			return;
		}

		payment.startProcessing();
		// Persist PROCESSING status immediately so other pods see it even if we crash
		// mid-flight. On recovery, the payment stays PROCESSING until a human or
		// compensation job resolves it.
		paymentRepository.save(payment);

		try {
			if (payment.getType() == PaymentType.FX_CONVERSION) {
				performFxConversion(payment);
			}

			accountServiceClient.debitAccount(
					payment.getSourceAccountId(), payment.getAmount(), paymentId);

			// For FX payments: credit convertedAmount (PLN), not the source-currency amount.
			// getAmountToCredit() handles this transparently — returns convertedAmount for FX,
			// original amount for all other payment types.
			accountServiceClient.creditAccount(
					payment.getTargetAccountId(), payment.getAmountToCredit(), paymentId);

			payment.complete();
			saveAndPublish(payment);

			meterRegistry.counter("payment.completed", "type", payment.getType().name()).increment();
			log.info("Payment {} COMPLETED successfully", paymentId);

		} catch (FxServiceWebClient.FxServiceUnavailableException ex) {
			// FX failed before any money moved — clean failure, no compensation needed
			log.error("FX conversion failed for payment {}: {}", paymentId, ex.getMessage());
			payment.fail("FX conversion unavailable: " + ex.getMessage());
			saveAndPublish(payment);
			meterRegistry.counter("payment.failed", "reason", "fx_service").increment();

		} catch (AccountServiceWebClient.AccountServiceUnavailableException ex) {
			// Debit OR credit failed. If debit succeeded and credit failed,
			// money has moved — see class Javadoc for compensation strategy.
			log.error("ACCOUNT SERVICE FAILED for payment {} — possible partial execution: {}",
					paymentId, ex.getMessage());
			payment.fail("Account Service unavailable: " + ex.getMessage());
			saveAndPublish(payment);
			meterRegistry.counter("payment.failed", "reason", "account_service").increment();
		}
	}

	private Payment loadPayment(String paymentId) {
		return paymentRepository.findById(PaymentId.of(paymentId))
				.orElseThrow(() -> new NoSuchElementException("Payment not found: " + paymentId));
	}

	/**
	 * Calls FX Service to lock an exchange rate and convert the payment amount.
	 * On success, calls payment.lockFxConversion() to record the result on the aggregate.
	 * <p>
	 * CRITICAL: After this method, payment.getAmountToCredit() returns the converted PLN amount.
	 * creditAccount() MUST be called after this — it reads getAmountToCredit() transparently.
	 * <p>
	 * If FX Service is unavailable (circuit breaker open or timeout):
	 * FxServiceUnavailableException is thrown → caught in executeUnderLock() → payment.fail().
	 * No money has moved at this point — clean failure, no compensation needed.
	 * <p>
	 * Why "BUY_BASE"?
	 * Customer is selling EUR (source) to buy PLN (target). In FX terminology:
	 * BUY_BASE = buy the base currency (EUR) from the customer's perspective.
	 * FX Service uses this to select the correct bid/ask side.
	 */
	private void performFxConversion(Payment payment) {
		String paymentId = payment.getId().toString();
		Money money = payment.getAmount();
		// Pair: source currency → PLN (e.g. "EURPLN", "USDPLN")
		String pair = money.getCurrency().getCode() + "PLN";

		FxServiceWebClient.FxConversionResult fx = fxServiceClient.convert(
				paymentId,
				payment.getSourceAccountId(),
				payment.getInitiatedBy(),
				pair,
				money.getAmount(),
				"BUY_BASE"
		);

		log.info("FX locked for payment {} — {} {} → {} PLN @ {} (fee: {}, convId: {})",
				paymentId, money.getAmount(), money.getCurrency().getCode(),
				fx.convertedAmount(), fx.appliedRate(), fx.fee(), fx.conversionId());

		// Record FX result on the aggregate — this is what creditAccount() will use.
		// convertedAmount is in PLN (target currency in all FinCore FX flows).
		Money convertedAmountMoney = Money.of(fx.convertedAmount(), Currency.PLN);
		payment.lockFxConversion(convertedAmountMoney, fx.conversionId());
	}

	/**
	 * Persist the payment and flush all accumulated domain events to the outbox.
	 * Called within an active @Transactional context — payment row + outbox rows
	 * are written atomically.
	 * <p>
	 * Note: pullDomainEvents() clears the in-memory list — idempotent, safe to call
	 * multiple times (second call returns empty list).
	 */
	private void saveAndPublish(Payment payment) {
		paymentRepository.save(payment);
		List<DomainEvent> events = payment.pullDomainEvents();
		events.forEach(event -> outboxEventPublisher.publish(event, "Payment"));
		log.debug("Saved payment {} ({}) and queued {} domain event(s) to outbox",
				payment.getId(), payment.getStatus(), events.size());
	}
}
