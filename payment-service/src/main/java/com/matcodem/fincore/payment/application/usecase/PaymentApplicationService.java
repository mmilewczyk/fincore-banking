package com.matcodem.fincore.payment.application.usecase;

import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.matcodem.fincore.payment.domain.domain.port.in.GetPaymentUseCase;
import com.matcodem.fincore.payment.domain.domain.port.in.InitiatePaymentUseCase;
import com.matcodem.fincore.payment.domain.domain.port.in.ProcessPaymentUseCase;
import com.matcodem.fincore.payment.domain.domain.port.out.AccountServiceClient;
import com.matcodem.fincore.payment.domain.domain.port.out.OutboxEventPublisher;
import com.matcodem.fincore.payment.domain.domain.port.out.PaymentLockService;
import com.matcodem.fincore.payment.domain.domain.port.out.PaymentRepository;
import com.matcodem.fincore.payment.domain.model.Payment;
import com.matcodem.fincore.payment.domain.model.PaymentId;

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
	@Timed(value = "payment.process", description = "Time to process a payment")
	public void processPayment(PaymentId paymentId) {

		Payment payment = paymentRepository.findById(paymentId)
				.orElseThrow(() -> new NoSuchElementException("Payment not found: " + paymentId));

		if (!payment.isPending()) {
			log.warn("Payment {} is not PENDING (status: {}), skipping", paymentId, payment.getStatus());
			return;
		}

		// ── Distributed Lock ───────────────────────────────────────
		// Lock BOTH account IDs, always in sorted order to prevent deadlocks.
		// Example: payment A locks [acc-1, acc-2], payment B locks [acc-2, acc-1]
		//          → without ordering, deadlock possible
		//          → with sorted ordering, both try acc-1 first → safe
		lockService.executeWithLock(
				payment.getSourceAccountId(),
				payment.getTargetAccountId(),
				() -> doProcess(payment)
		);
	}

	/**
	 * Actual processing — runs inside distributed lock.
	 * Uses a nested transaction to ensure atomicity of DB updates.
	 */
	@Transactional
	protected void doProcess(Payment payment) {
		// Re-fetch inside transaction + lock (prevents stale read)
		Payment freshPayment = paymentRepository.findById(payment.getId())
				.orElseThrow(() -> new NoSuchElementException("Payment disappeared: " + payment.getId()));

		// Double-check status after acquiring lock (another thread might have processed it)
		if (!freshPayment.isPending()) {
			log.warn("Payment {} processed by another instance, skipping", freshPayment.getId());
			return;
		}

		try {
			// Mark as processing
			freshPayment.startProcessing();
			paymentRepository.save(freshPayment);

			String reference = freshPayment.getId().toString();

			// Debit source account
			accountServiceClient.debitAccount(
					freshPayment.getSourceAccountId(),
					freshPayment.getAmount(),
					reference
			);

			// Credit target account
			accountServiceClient.creditAccount(
					freshPayment.getTargetAccountId(),
					freshPayment.getAmount(),
					reference
			);

			// Mark as completed + save + publish via outbox
			freshPayment.complete();
			paymentRepository.save(freshPayment);

			var events = freshPayment.pullDomainEvents();
			events.forEach(event -> outboxEventPublisher.publish(event, "Payment"));

			meterRegistry.counter("payment.completed").increment();
			log.info("Payment {} completed successfully", freshPayment.getId());

		} catch (Exception ex) {
			log.error("Payment {} failed during processing: {}", freshPayment.getId(), ex.getMessage(), ex);

			freshPayment.fail(ex.getMessage());
			paymentRepository.save(freshPayment);

			var events = freshPayment.pullDomainEvents();
			events.forEach(event -> outboxEventPublisher.publish(event, "Payment"));

			meterRegistry.counter("payment.failed").increment();
			throw ex; // re-throw so caller knows it failed
		}
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
