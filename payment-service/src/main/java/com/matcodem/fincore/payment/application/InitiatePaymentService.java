package com.matcodem.fincore.payment.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.matcodem.fincore.payment.domain.event.DomainEvent;
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
 * Handles payment initiation - entry point for all new payments.
 *
 * ── TRANSACTION BOUNDARY ──────────────────────────────────────────────────────
 * Single @Transactional wraps the entire method:
 *   - idempotency check (SELECT)
 *   - account pre-flight check (external call - see note below)
 *   - Payment.initiate() (in-memory)
 *   - paymentRepository.save() (INSERT payments row)
 *   - outboxEventPublisher.publish() (INSERT outbox_messages row)
 *
 * The payment row and outbox row are written atomically. If either fails,
 * both roll back - no orphaned outbox messages, no unnotified payments.
 *
 * Note on external call within transaction:
 *   accountServiceClient.getAccountInfo() is called inside the transaction.
 *   This is intentional: if the account service is down, we reject immediately
 *   without persisting anything. The transaction is short (no lock wait here),
 *   so holding the connection during one HTTP call (~50ms) is acceptable.
 *   If account service latency becomes a concern, move the pre-flight check
 *   outside the transaction boundary.
 *
 * ── IDEMPOTENCY ───────────────────────────────────────────────────────────────
 * The idempotency key has a UNIQUE constraint in the DB (V1 migration).
 * The SELECT check here is a fast path to return early without hitting the
 * constraint. The constraint is the true guarantee against duplicates -
 * two concurrent requests with the same key will race on INSERT and one will
 * get a ConstraintViolationException, which is caught by Spring and translated
 * to a DataIntegrityViolationException. That exception propagates up and the
 * client should treat it as a 409 or retry with GET.
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

		// Fast-path idempotency: same key seen before -> return existing result
		var existing = paymentRepository.findByIdempotencyKey(command.idempotencyKey());
		if (existing.isPresent()) {
			log.info("Idempotent hit - returning existing payment {} for key {}",
					existing.get().getId(), command.idempotencyKey());
			meterRegistry.counter("payment.idempotent.hits").increment();
			return existing.get();
		}

		// Pre-flight: fail fast if source account is not active.
		// Prevents creating a PENDING payment that will 100% fail during processing
		// (spares fraud service, lock service, and account service from wasted work).
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

		// Pull domain events BEFORE save - the aggregate has events from initiate().
		// Some repository implementations return a new object instance (e.g. after
		// mapping to/from JPA entity), which would have an empty events list.
		// Pulling before save ensures we capture the PaymentInitiatedEvent.
		List<DomainEvent> events = payment.pullDomainEvents();

		Payment saved = paymentRepository.save(payment);

		// Publish to outbox in same transaction - atomically with the payment INSERT.
		events.forEach(event -> outboxEventPublisher.publish(event, "Payment"));

		log.info("Payment initiated: id={} type={} amount={} key={}",
				saved.getId(), saved.getType(), saved.getAmount(), command.idempotencyKey());
		meterRegistry.counter("payment.initiated", "type", command.type().name()).increment();

		return saved;
	}
}
