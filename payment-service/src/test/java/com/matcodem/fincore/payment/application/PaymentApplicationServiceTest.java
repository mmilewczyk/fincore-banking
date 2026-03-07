package com.matcodem.fincore.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.matcodem.fincore.payment.application.usecase.PaymentApplicationService;
import com.matcodem.fincore.payment.domain.port.in.InitiatePaymentUseCase;
import com.matcodem.fincore.payment.domain.port.out.AccountServiceClient;
import com.matcodem.fincore.payment.domain.port.out.OutboxEventPublisher;
import com.matcodem.fincore.payment.domain.port.out.OutboxRepository;
import com.matcodem.fincore.payment.domain.port.out.PaymentLockService;
import com.matcodem.fincore.payment.domain.port.out.PaymentRepository;
import com.matcodem.fincore.payment.domain.model.Currency;
import com.matcodem.fincore.payment.domain.model.IdempotencyKey;
import com.matcodem.fincore.payment.domain.model.Money;
import com.matcodem.fincore.payment.domain.model.Payment;
import com.matcodem.fincore.payment.domain.model.PaymentStatus;
import com.matcodem.fincore.payment.domain.model.PaymentType;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentApplicationService")
class PaymentApplicationServiceTest {

	@Mock
	private PaymentRepository paymentRepository;
	@Mock
	private OutboxRepository outboxRepository;
	@Mock
	private OutboxEventPublisher outboxEventPublisher;
	@Mock
	private AccountServiceClient accountServiceClient;
	@Mock
	private PaymentLockService lockService;

	private MeterRegistry meterRegistry;
	private PaymentApplicationService service;

	private static final String SOURCE = "acc-source";
	private static final String TARGET = "acc-target";
	private static final Money AMOUNT = Money.of("100.00", Currency.PLN);
	private static final String USER = "user-123";

	@BeforeEach
	void setUp() {
		meterRegistry = new SimpleMeterRegistry();
		service = new PaymentApplicationService(
				paymentRepository, outboxRepository, outboxEventPublisher,
				accountServiceClient, lockService, meterRegistry
		);
	}

	@Nested
	@DisplayName("Idempotency")
	class Idempotency {

		@Test
		@DisplayName("should return existing payment when idempotency key already used")
		void shouldReturnExistingPayment() {
			IdempotencyKey key = IdempotencyKey.generate();
			Payment existing = Payment.initiate(key, SOURCE, TARGET, AMOUNT,
					PaymentType.INTERNAL_TRANSFER, USER);

			when(paymentRepository.findByIdempotencyKey(key)).thenReturn(Optional.of(existing));

			var command = new InitiatePaymentUseCase.InitiatePaymentCommand(
					key, SOURCE, TARGET, AMOUNT, PaymentType.INTERNAL_TRANSFER, USER);

			Payment result = service.initiatePayment(command);

			assertThat(result).isEqualTo(existing);

			// Should NOT call account service or save again
			verify(accountServiceClient, never()).getAccountInfo(any());
			verify(paymentRepository, never()).save(any());
		}

		@Test
		@DisplayName("idempotent hit should be counted in metrics")
		void shouldIncrementIdempotentCounter() {
			IdempotencyKey key = IdempotencyKey.generate();
			Payment existing = Payment.initiate(key, SOURCE, TARGET, AMOUNT,
					PaymentType.INTERNAL_TRANSFER, USER);

			when(paymentRepository.findByIdempotencyKey(key)).thenReturn(Optional.of(existing));

			var command = new InitiatePaymentUseCase.InitiatePaymentCommand(
					key, SOURCE, TARGET, AMOUNT, PaymentType.INTERNAL_TRANSFER, USER);

			service.initiatePayment(command);

			double count = meterRegistry.counter("payment.idempotent.hits").count();
			assertThat(count).isEqualTo(1.0);
		}
	}

	@Nested
	@DisplayName("Payment initiation")
	class Initiation {

		@Test
		@DisplayName("should save payment and publish outbox event for new payment")
		void shouldSaveAndPublishOutbox() {
			IdempotencyKey key = IdempotencyKey.generate();

			when(paymentRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty());
			when(accountServiceClient.getAccountInfo(SOURCE))
					.thenReturn(new AccountServiceClient.AccountInfo(SOURCE, "PLN", true));
			when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

			var command = new InitiatePaymentUseCase.InitiatePaymentCommand(
					key, SOURCE, TARGET, AMOUNT, PaymentType.INTERNAL_TRANSFER, USER);

			Payment result = service.initiatePayment(command);

			assertThat(result.getStatus()).isEqualTo(PaymentStatus.PENDING);
			verify(paymentRepository).save(any(Payment.class));
			verify(outboxEventPublisher).publish(any(), eq("Payment"));
		}

		@Test
		@DisplayName("should reject initiation if source account is not active")
		void shouldRejectInactiveAccount() {
			IdempotencyKey key = IdempotencyKey.generate();

			when(paymentRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty());
			when(accountServiceClient.getAccountInfo(SOURCE))
					.thenReturn(new AccountServiceClient.AccountInfo(SOURCE, "PLN", false));

			var command = new InitiatePaymentUseCase.InitiatePaymentCommand(
					key, SOURCE, TARGET, AMOUNT, PaymentType.INTERNAL_TRANSFER, USER);

			assertThatThrownBy(() -> service.initiatePayment(command))
					.isInstanceOf(IllegalStateException.class)
					.hasMessageContaining("not active");
		}
	}

	@Nested
	@DisplayName("Lock behaviour")
	class LockBehaviour {

		@Test
		@DisplayName("processPayment should delegate to lockService.executeWithLock")
		void shouldUseLock() {
			Payment payment = Payment.initiate(
					IdempotencyKey.generate(), SOURCE, TARGET, AMOUNT,
					PaymentType.INTERNAL_TRANSFER, USER
			);

			when(paymentRepository.findById(payment.getId()))
					.thenReturn(Optional.of(payment));

			doAnswer(inv -> {
				Runnable action = inv.getArgument(2);
				action.run();
				return null;
			}).when(lockService).executeWithLock(any(), any(), any());

			// Re-fetch inside doProcess uses the same mock
			when(paymentRepository.findById(payment.getId()))
					.thenReturn(Optional.of(payment));
			when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

			service.processPayment(payment.getId());

			verify(lockService).executeWithLock(eq(SOURCE), eq(TARGET), any());
		}

		@Test
		@DisplayName("should skip processing if payment is not PENDING")
		void shouldSkipNonPendingPayment() {
			Payment payment = Payment.initiate(
					IdempotencyKey.generate(), SOURCE, TARGET, AMOUNT,
					PaymentType.INTERNAL_TRANSFER, USER
			);
			payment.startProcessing(); // now PROCESSING

			when(paymentRepository.findById(payment.getId()))
					.thenReturn(Optional.of(payment));

			service.processPayment(payment.getId());

			// Lock should never be acquired for non-pending payments
			verify(lockService, never()).executeWithLock(any(), any(), any());
		}
	}
}