package com.matcodem.fincore.payment.application.usecase;

import static com.matcodem.fincore.payment.domain.model.PaymentType.FX_CONVERSION;

import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.matcodem.fincore.payment.adapter.out.client.AccountServiceWebClient;
import com.matcodem.fincore.payment.adapter.out.client.FxServiceWebClient;
import com.matcodem.fincore.payment.domain.model.Money;
import com.matcodem.fincore.payment.domain.model.Payment;
import com.matcodem.fincore.payment.domain.model.PaymentId;
import com.matcodem.fincore.payment.domain.model.PaymentStatus;
import com.matcodem.fincore.payment.domain.port.in.GetPaymentUseCase;
import com.matcodem.fincore.payment.domain.port.in.InitiatePaymentUseCase;
import com.matcodem.fincore.payment.domain.port.in.ProcessPaymentUseCase;
import com.matcodem.fincore.payment.domain.port.out.AccountServiceClient;
import com.matcodem.fincore.payment.domain.port.out.OutboxEventPublisher;
import com.matcodem.fincore.payment.domain.port.out.PaymentLockService;
import com.matcodem.fincore.payment.domain.port.out.PaymentRepository;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentApplicationService implements
		InitiatePaymentUseCase,
		ProcessPaymentUseCase,
		GetPaymentUseCase {

	private final PaymentRepository paymentRepository;
	private final FxServiceWebClient fxServiceClient;
	private final OutboxEventPublisher outboxEventPublisher;
	private final AccountServiceClient accountServiceClient;
	private final PaymentLockService lockService;
	private final MeterRegistry meterRegistry;

	@Override
	@Transactional
	@Timed(value = "payment.initiate", description = "Time to initiate a payment")
	public Payment initiatePayment(InitiatePaymentCommand command) {
		// If we've seen this key before, return the existing result.
		// This handles client retries safely — same request = same response.
		var existing = paymentRepository.findByIdempotencyKey(command.idempotencyKey());
		if (existing.isPresent()) {
			log.info("Idempotent request — returning existing payment: {} for key: {}",
					existing.get().getId(), command.idempotencyKey());
			meterRegistry.counter("payment.idempotent.hits").increment();
			return existing.get();
		}

		var sourceInfo = accountServiceClient.getAccountInfo(command.sourceAccountId());
		if (!sourceInfo.active()) {
			throw new IllegalStateException("Source account is not active: " + command.sourceAccountId());
		}

		Payment payment = Payment.initiate(
				command.idempotencyKey(),
				command.sourceAccountId(),
				command.targetAccountId(),
				command.amount(),
				command.type(),
				command.initiatedBy()
		);

		Payment saved = paymentRepository.save(payment);

		var events = saved.pullDomainEvents();
		events.forEach(event -> outboxEventPublisher.publish(event, "Payment"));

		log.info("Payment initiated: {} (idempotencyKey: {})", saved.getId(), command.idempotencyKey());
		return saved;
	}

	@Override
	@Timed(value = "payment.process.duration")
	public void processPayment(String paymentId) {
		log.info("Processing payment: {}", paymentId);

		Payment payment = loadPayment(paymentId);

		if (!payment.getStatus().equals(PaymentStatus.PENDING)) {
			log.warn("Payment {} is not PENDING ({}), skipping", paymentId, payment.getStatus());
			return;
		}

		lockService.executeWithLock(
				payment.getSourceAccountId(),
				payment.getTargetAccountId(),
				() -> {
					// Re-fetch inside lock to prevent stale reads
					Payment locked = loadPayment(paymentId);
					if (!locked.getStatus().equals(PaymentStatus.PENDING)) {
						log.warn("Payment {} already processed by another instance", paymentId);
						return;
					}

					locked.startProcessing();

					try {
						// Step 1: FX conversion if needed
						if (FX_CONVERSION.equals(locked.getType())) {
							performFxConversion(locked);
						}

						// Step 2: Debit source account
						accountServiceClient.debitAccount(
								locked.getSourceAccountId(),
								locked.getAmount(),
								paymentId
						);

						// Step 3: Credit target account
						accountServiceClient.creditAccount(
								locked.getTargetAccountId(),
								locked.getAmount(),
								paymentId
						);

						// Step 4: Complete
						locked.complete();
						saveWithOutbox(locked);
						meterRegistry.counter("payment.completed").increment();
						log.info("Payment {} COMPLETED", paymentId);

					} catch (AccountServiceWebClient.AccountServiceUnavailableException ex) {
						log.error("Account Service failed for payment {}: {}", paymentId, ex.getMessage());
						locked.fail("Account Service unavailable: " + ex.getMessage());
						saveWithOutbox(locked);
						meterRegistry.counter("payment.failed", "reason", "account_service").increment();

					} catch (FxServiceWebClient.FxServiceUnavailableException ex) {
						log.error("FX Service failed for payment {}: {}", paymentId, ex.getMessage());
						locked.fail("FX conversion unavailable: " + ex.getMessage());
						saveWithOutbox(locked);
						meterRegistry.counter("payment.failed", "reason", "fx_service").increment();
					}
				}
		);
	}

	@Override
	@Transactional
	public void rejectForFraud(String paymentId, String reason) {
		log.warn("Rejecting payment {} for fraud: {}", paymentId, reason);
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
		if (payment.getStatus().equals(PaymentStatus.PENDING) ||
				payment.getStatus().equals(PaymentStatus.PROCESSING)) {
			payment.fail(reason);
			saveWithOutbox(payment);
		}
	}

	@Override
	@Transactional
	public void initiateReversalIfNeeded(String paymentId, String reason) {
		Payment payment = loadPayment(paymentId);

		switch (payment.getStatus()) {
			case COMPLETED -> {
				log.error("REVERSAL NEEDED for completed payment {} — fraud confirmed: {}", paymentId, reason);
				// Reverse the money movement
				try {
					accountServiceClient.debitAccount(
							payment.getTargetAccountId(),
							payment.getAmount(),
							"REVERSAL-" + paymentId
					);
					accountServiceClient.creditAccount(
							payment.getSourceAccountId(),
							payment.getAmount(),
							"REVERSAL-" + paymentId
					);
					log.info("Reversal completed for payment {}", paymentId);
					meterRegistry.counter("payment.reversal.completed").increment();
				} catch (Exception ex) {
					log.error("CRITICAL: Reversal FAILED for payment {} — MANUAL INTERVENTION REQUIRED: {}",
							paymentId, ex.getMessage());
					meterRegistry.counter("payment.reversal.failed").increment();
					// Alert ops — in production: PagerDuty, create JIRA ticket
				}
			}
			case PENDING, PROCESSING -> rejectForFraud(paymentId, "Fraud confirmed: " + reason);
			default -> log.info("Payment {} already in terminal state {}, no reversal needed",
					paymentId, payment.getStatus());
		}
	}

	private void performFxConversion(Payment payment) {
		Money money = payment.getAmount();
		// Build pair symbol: source currency + PLN, e.g. "EURPLN"
		String pair = money.getCurrency().getCode() + "PLN";

		FxServiceWebClient.FxConversionResult fx = fxServiceClient.convert(
				payment.getId().value().toString(),
				payment.getSourceAccountId(),
				payment.getInitiatedBy(),
				pair,
				money.getAmount(),
				"BUY_BASE"
		);

		log.info("FX applied to payment {} — converted amount: {}", payment.getId(), fx.convertedAmount());
	}

	@Transactional
	protected void saveWithOutbox(Payment payment) {
		paymentRepository.save(payment);
		for (var event : payment.pullDomainEvents()) {
			outboxEventPublisher.publish(event, "Payment");
		}
	}

	private Payment loadPayment(String paymentId) {
		return paymentRepository.findByIdString(paymentId)
				.orElseThrow(() -> new NoSuchElementException("Payment not found: " + paymentId));
	}

	@Override
	@Transactional(readOnly = true)
	public Payment getPayment(PaymentId paymentId) {
		return paymentRepository.findById(paymentId)
				.orElseThrow(() -> new NoSuchElementException("Payment not found: " + paymentId));
	}

	@Override
	@Transactional(readOnly = true)
	public List<Payment> getPaymentsByAccount(String accountId) {
		var sent = paymentRepository.findBySourceAccountId(accountId);
		var received = paymentRepository.findByTargetAccountId(accountId);
		return java.util.stream.Stream.concat(sent.stream(), received.stream())
				.sorted(java.util.Comparator.comparing(Payment::getCreatedAt).reversed())
				.toList();
	}
}
