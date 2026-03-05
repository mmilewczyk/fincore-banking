package com.matcodem.fincore.payment.adapter.in.web;

import io.micrometer.core.annotation.Timed;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

import com.matcodem.fincore.payment.adapter.in.web.dto.InitiatePaymentRequest;
import com.matcodem.fincore.payment.adapter.in.web.dto.PaymentResponse;
import com.matcodem.fincore.payment.domain.domain.port.in.GetPaymentUseCase;
import com.matcodem.fincore.payment.domain.domain.port.in.InitiatePaymentUseCase;
import com.matcodem.fincore.payment.domain.domain.port.in.ProcessPaymentUseCase;
import com.matcodem.fincore.payment.domain.model.Currency;
import com.matcodem.fincore.payment.domain.model.IdempotencyKey;
import com.matcodem.fincore.payment.domain.model.Money;
import com.matcodem.fincore.payment.domain.model.Payment;
import com.matcodem.fincore.payment.domain.model.PaymentId;
import com.matcodem.fincore.payment.domain.model.PaymentType;

@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

	private final InitiatePaymentUseCase initiatePaymentUseCase;
	private final GetPaymentUseCase getPaymentUseCase;
	private final ProcessPaymentUseCase processPaymentUseCase;

	/**
	 * Initiates a new payment.
	 *
	 * IDEMPOTENCY: Client must send X-Idempotency-Key header (UUID).
	 * If the same key is sent again (retry), the original payment is returned.
	 * HTTP 200 = existing payment returned (idempotent hit)
	 * HTTP 201 = new payment created
	 */
	@PostMapping
	@Timed(value = "api.payment.initiate")
	@PreAuthorize("hasRole('ROLE_USER')")
	public ResponseEntity<PaymentResponse> initiatePayment(
			@Valid @RequestBody InitiatePaymentRequest request,
			@RequestHeader(value = "X-Idempotency-Key") String idempotencyKeyHeader,
			@AuthenticationPrincipal Jwt jwt) {

		IdempotencyKey idempotencyKey = IdempotencyKey.of(idempotencyKeyHeader);
		String initiatedBy = jwt.getSubject();

		// Check idempotency before creating command
		var command = new InitiatePaymentUseCase.InitiatePaymentCommand(
				idempotencyKey,
				request.sourceAccountId(),
				request.targetAccountId(),
				Money.of(request.amount(), Currency.fromCode(request.currency())),
				PaymentType.valueOf(request.type()),
				initiatedBy
		);

		Payment payment = initiatePaymentUseCase.initiatePayment(command);
		PaymentResponse response = toResponse(payment);

		// 200 = idempotent (already existed), 201 = newly created
		boolean isNew = payment.getCreatedAt().equals(payment.getUpdatedAt());
		if (isNew) {
			return ResponseEntity
					.created(URI.create("/api/v1/payments/" + payment.getId()))
					.body(response);
		}
		return ResponseEntity.ok(response);
	}

	@GetMapping("/{paymentId}")
	@PreAuthorize("hasRole('ROLE_USER')")
	public ResponseEntity<PaymentResponse> getPayment(
			@PathVariable String paymentId,
			@AuthenticationPrincipal Jwt jwt) {

		Payment payment = getPaymentUseCase.getPayment(PaymentId.of(paymentId));

		// Users can only see their own payments
		if (!payment.getInitiatedBy().equals(jwt.getSubject()) && !isAdmin(jwt)) {
			return ResponseEntity.status(403).build();
		}

		return ResponseEntity.ok(toResponse(payment));
	}

	@GetMapping("/account/{accountId}")
	@PreAuthorize("hasRole('ROLE_USER')")
	public ResponseEntity<List<PaymentResponse>> getPaymentsByAccount(
			@PathVariable String accountId) {

		List<PaymentResponse> payments = getPaymentUseCase.getPaymentsByAccount(accountId)
				.stream().map(this::toResponse).toList();
		return ResponseEntity.ok(payments);
	}

	/**
	 * Manually trigger processing — normally done async by a scheduler.
	 * Useful for testing and admin operations.
	 */
	@PostMapping("/{paymentId}/process")
	@PreAuthorize("hasRole('ROLE_ADMIN')")
	public ResponseEntity<Void> processPayment(@PathVariable String paymentId) {
		processPaymentUseCase.processPayment(PaymentId.of(paymentId));
		return ResponseEntity.accepted().build();
	}

	private PaymentResponse toResponse(Payment p) {
		return new PaymentResponse(
				p.getId().toString(),
				p.getIdempotencyKey().value(),
				p.getSourceAccountId(),
				p.getTargetAccountId(),
				p.getAmount().getAmount(),
				p.getAmount().getCurrency().getCode(),
				p.getType().name(),
				p.getStatus().name(),
				p.getFailureReason(),
				p.getInitiatedBy(),
				p.getCreatedAt(),
				p.getUpdatedAt()
		);
	}

	private boolean isAdmin(Jwt jwt) {
		var roles = jwt.getClaimAsStringList("roles");
		return roles != null && roles.contains("ROLE_ADMIN");
	}
}