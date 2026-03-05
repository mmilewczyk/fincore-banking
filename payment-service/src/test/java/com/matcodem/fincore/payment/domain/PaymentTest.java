package com.matcodem.fincore.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.matcodem.fincore.payment.domain.event.PaymentCompletedEvent;
import com.matcodem.fincore.payment.domain.event.PaymentFailedEvent;
import com.matcodem.fincore.payment.domain.event.PaymentFraudRejectedEvent;
import com.matcodem.fincore.payment.domain.event.PaymentInitiatedEvent;
import com.matcodem.fincore.payment.domain.model.Currency;
import com.matcodem.fincore.payment.domain.model.IdempotencyKey;
import com.matcodem.fincore.payment.domain.model.Money;
import com.matcodem.fincore.payment.domain.model.Payment;
import com.matcodem.fincore.payment.domain.model.PaymentStatus;
import com.matcodem.fincore.payment.domain.model.PaymentType;

@DisplayName("Payment Aggregate")
class PaymentTest {

	private static final String SOURCE_ACCOUNT = "acc-source-001";
	private static final String TARGET_ACCOUNT = "acc-target-002";
	private static final Money AMOUNT = Money.of("500.00", Currency.PLN);
	private static final String INITIATED_BY = "user-123";

	private Payment payment;

	@BeforeEach
	void setUp() {
		payment = Payment.initiate(
				IdempotencyKey.generate(),
				SOURCE_ACCOUNT,
				TARGET_ACCOUNT,
				AMOUNT,
				PaymentType.INTERNAL_TRANSFER,
				INITIATED_BY
		);
		payment.pullDomainEvents(); // clear initiation event
	}

	@Nested
	@DisplayName("Initiation")
	class Initiation {

		@Test
		@DisplayName("should create payment in PENDING status")
		void shouldCreatePending() {
			Payment p = Payment.initiate(
					IdempotencyKey.generate(), SOURCE_ACCOUNT, TARGET_ACCOUNT,
					AMOUNT, PaymentType.INTERNAL_TRANSFER, INITIATED_BY
			);

			assertThat(p.getStatus()).isEqualTo(PaymentStatus.PENDING);
			assertThat(p.isPending()).isTrue();
			assertThat(p.getId()).isNotNull();
		}

		@Test
		@DisplayName("should record PaymentInitiatedEvent")
		void shouldRecordInitiatedEvent() {
			Payment p = Payment.initiate(
					IdempotencyKey.generate(), SOURCE_ACCOUNT, TARGET_ACCOUNT,
					AMOUNT, PaymentType.INTERNAL_TRANSFER, INITIATED_BY
			);

			var events = p.pullDomainEvents();
			assertThat(events).hasSize(1);
			assertThat(events.getFirst()).isInstanceOf(PaymentInitiatedEvent.class);
		}

		@Test
		@DisplayName("should reject zero amount")
		void shouldRejectZeroAmount() {
			assertThatThrownBy(() -> Payment.initiate(
					IdempotencyKey.generate(), SOURCE_ACCOUNT, TARGET_ACCOUNT,
					Money.of("0.00", Currency.PLN), PaymentType.INTERNAL_TRANSFER, INITIATED_BY
			)).isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("positive");
		}

		@Test
		@DisplayName("should reject same source and target account")
		void shouldRejectSameAccounts() {
			assertThatThrownBy(() -> Payment.initiate(
					IdempotencyKey.generate(), SOURCE_ACCOUNT, SOURCE_ACCOUNT,
					AMOUNT, PaymentType.INTERNAL_TRANSFER, INITIATED_BY
			)).isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("differ");
		}

		@Test
		@DisplayName("should reject null idempotency key")
		void shouldRejectNullIdempotencyKey() {
			assertThatThrownBy(() -> Payment.initiate(
					null, SOURCE_ACCOUNT, TARGET_ACCOUNT,
					AMOUNT, PaymentType.INTERNAL_TRANSFER, INITIATED_BY
			)).isInstanceOf(NullPointerException.class);
		}
	}

	@Nested
	@DisplayName("Processing lifecycle")
	class ProcessingLifecycle {

		@Test
		@DisplayName("PENDING → PROCESSING → COMPLETED happy path")
		void happyPath() {
			payment.startProcessing();
			assertThat(payment.isProcessing()).isTrue();

			payment.complete();
			assertThat(payment.isCompleted()).isTrue();
			assertThat(payment.isTerminal()).isTrue();

			var events = payment.pullDomainEvents();
			assertThat(events).hasSize(1);
			assertThat(events.getFirst()).isInstanceOf(PaymentCompletedEvent.class);
		}

		@Test
		@DisplayName("should not allow startProcessing on non-PENDING payment")
		void shouldNotStartProcessingTwice() {
			payment.startProcessing();

			assertThatThrownBy(() -> payment.startProcessing())
					.isInstanceOf(IllegalStateException.class)
					.hasMessageContaining("PROCESSING");
		}

		@Test
		@DisplayName("should not complete a PENDING payment (must be PROCESSING first)")
		void shouldNotCompleteFromPending() {
			assertThatThrownBy(() -> payment.complete())
					.isInstanceOf(IllegalStateException.class);
		}

		@Test
		@DisplayName("PENDING → FAILED")
		void shouldFailFromPending() {
			payment.fail("Insufficient funds");

			assertThat(payment.isFailed()).isTrue();
			assertThat(payment.getFailureReason()).isEqualTo("Insufficient funds");

			var events = payment.pullDomainEvents();
			assertThat(events).hasSize(1);
			assertThat(events.getFirst()).isInstanceOf(PaymentFailedEvent.class);
		}

		@Test
		@DisplayName("PROCESSING → FAILED")
		void shouldFailFromProcessing() {
			payment.startProcessing();
			payment.fail("Account frozen");

			assertThat(payment.isFailed()).isTrue();
		}

		@Test
		@DisplayName("should not fail a completed payment")
		void shouldNotFailCompleted() {
			payment.startProcessing();
			payment.complete();

			assertThatThrownBy(() -> payment.fail("Late failure"))
					.isInstanceOf(IllegalStateException.class)
					.hasMessageContaining("terminal");
		}
	}

	@Nested
	@DisplayName("Cancellation")
	class Cancellation {

		@Test
		@DisplayName("should cancel PENDING payment")
		void shouldCancelPending() {
			payment.cancel("User requested");

			assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
			assertThat(payment.isTerminal()).isTrue();
		}

		@Test
		@DisplayName("should not cancel PROCESSING payment")
		void shouldNotCancelProcessing() {
			payment.startProcessing();

			assertThatThrownBy(() -> payment.cancel("Too late"))
					.isInstanceOf(IllegalStateException.class)
					.hasMessageContaining("PENDING");
		}
	}

	@Nested
	@DisplayName("Fraud rejection")
	class FraudRejection {

		@Test
		@DisplayName("should reject PENDING payment as fraudulent")
		void shouldRejectPending() {
			payment.rejectAsFraudulent("Velocity rule exceeded");

			assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REJECTED_FRAUD);
			assertThat(payment.isTerminal()).isTrue();

			var events = payment.pullDomainEvents();
			assertThat(events.getFirst()).isInstanceOf(PaymentFraudRejectedEvent.class);
		}
	}

	@Nested
	@DisplayName("Domain events")
	class DomainEvents {

		@Test
		@DisplayName("pullDomainEvents clears the event list")
		void shouldClearEventsAfterPull() {
			payment.startProcessing();
			payment.complete();

			var events = payment.pullDomainEvents();
			assertThat(events).hasSize(1);

			// Second pull should be empty
			assertThat(payment.pullDomainEvents()).isEmpty();
		}
	}
}