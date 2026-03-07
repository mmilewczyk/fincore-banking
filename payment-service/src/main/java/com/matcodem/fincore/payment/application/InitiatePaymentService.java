package com.matcodem.fincore.payment.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.matcodem.fincore.payment.domain.model.Payment;
import com.matcodem.fincore.payment.domain.port.in.InitiatePaymentUseCase;
import com.matcodem.fincore.payment.domain.port.out.AccountServiceClient;
import com.matcodem.fincore.payment.domain.port.out.OutboxEventPublisher;
import com.matcodem.fincore.payment.domain.port.out.PaymentRepository;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Handles payment initiation — the entry point for all new payments.
 * <p>
 * Responsibilities:
 * 1. Idempotency: return existing payment if key already seen
 * 2. Pre-flight check: source account must be active
 * 3. Create Payment aggregate, persist, publish PaymentInitiatedEvent via outbox
 * <p>
 * Does NOT handle processing (see ProcessPaymentService) or querying (QueryPaymentService).
 * <p>
 * Transaction boundary: single DB transaction — payment row + outbox message
 * written atomically, ensuring at-least-once delivery guarantee.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InitiatePaymentService implements InitiatePaymentUseCase {

	private final PaymentRepository paymentRepository;
	private final AccountServiceClient accountServiceClient;
	private final OutboxEventPublisher outboxEventPublisher;
	private final MeterRegistry meterRegistry;

	@Override
	@Transactional
	@Timed(value = "payment.initiate", description = "Time to initiate a payment")
	public Payment initiatePayment(InitiatePaymentCommand command) {

		// Idempotency check — same key = same response, no re-processing
		var existing = paymentRepository.findByIdempotencyKey(command.idempotencyKey());
		if (existing.isPresent()) {
			log.info("Idempotent hit — returning existing payment {} for key {}",
					existing.get().getId(), command.idempotencyKey());
			meterRegistry.counter("payment.idempotent.hits").increment();
			return existing.get();
		}

		// Pre-flight: reject immediately if source account is inactive/frozen
		// Avoids creating a payment that will definitely fail during processing
		AccountServiceClient.AccountInfo sourceInfo =
				accountServiceClient.getAccountInfo(command.sourceAccountId());
		if (!sourceInfo.active()) {
			throw new IllegalStateException(
					"Source account is not active: " + command.sourceAccountId());
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

		// Publish in same transaction via outbox — never lost even on crash
		saved.pullDomainEvents()
				.forEach(event -> outboxEventPublisher.publish(event, "Payment"));

		log.info("Payment initiated: {} type={} amount={} (key: {})",
				saved.getId(), saved.getType(), saved.getAmount(), command.idempotencyKey());
		meterRegistry.counter("payment.initiated", "type", command.type().name()).increment();

		return saved;
	}
}
