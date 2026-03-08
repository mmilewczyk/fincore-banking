package com.matcodem.fincore.payment.infrastructure.config;

import java.net.URI;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import org.hibernate.exception.LockAcquisitionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.matcodem.fincore.payment.adapter.out.client.AccountServiceWebClient;
import com.matcodem.fincore.payment.adapter.out.client.FxServiceWebClient;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

/**
 * Global exception handler — RFC 7807 ProblemDetail responses.
 * <p>
 * Rules:
 * - 4xx → log.warn (client error, expected, high-frequency)
 * - 5xx → log.error with full stack trace (our bug, alert-worthy)
 * - Never expose stack traces, class names, or internal messages to the client
 * - Each exception type maps to a stable, machine-readable error type URI
 * <p>
 * Stability contract: error type URIs (fincore.bank.pl/errors/...) are stable
 * API contracts — clients may build logic on them. Don't rename them without
 * a deprecation cycle.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(NoSuchElementException.class)
	public ResponseEntity<ProblemDetail> handleNotFound(NoSuchElementException ex) {
		log.warn("Resource not found: {}", ex.getMessage());
		return problem(HttpStatus.NOT_FOUND, "not-found", "Resource Not Found", ex.getMessage());
	}

	@ExceptionHandler(IllegalStateException.class)
	public ResponseEntity<ProblemDetail> handleIllegalState(IllegalStateException ex) {
		log.warn("Domain rule violation: {}", ex.getMessage());
		return problem(HttpStatus.UNPROCESSABLE_ENTITY, "domain-rule-violation",
				"Business Rule Violation", ex.getMessage());
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex) {
		log.warn("Invalid argument: {}", ex.getMessage());
		return problem(HttpStatus.BAD_REQUEST, "invalid-argument", "Invalid Request", ex.getMessage());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
		String details = ex.getBindingResult().getFieldErrors().stream()
				.map(fe -> "'%s': %s".formatted(fe.getField(), fe.getDefaultMessage()))
				.collect(Collectors.joining(", "));
		log.warn("Validation failed: {}", details);
		return problem(HttpStatus.BAD_REQUEST, "validation-failed", "Validation Failed", details);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex) {
		log.warn("Constraint violation: {}", ex.getMessage());
		return problem(HttpStatus.BAD_REQUEST, "constraint-violation",
				"Constraint Violation", ex.getMessage());
	}

	@ExceptionHandler(MissingRequestHeaderException.class)
	public ResponseEntity<ProblemDetail> handleMissingHeader(MissingRequestHeaderException ex) {
		log.warn("Missing required header: {}", ex.getHeaderName());
		return problem(HttpStatus.BAD_REQUEST, "missing-header",
				"Missing Required Header",
				"Required header '%s' is missing".formatted(ex.getHeaderName()));
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
		log.warn("Type mismatch for parameter '{}': {}", ex.getName(), ex.getMessage());
		return problem(HttpStatus.BAD_REQUEST, "invalid-parameter",
				"Invalid Parameter", "Invalid value for parameter '%s'".formatted(ex.getName()));
	}

	/**
	 * Distributed lock timeout — payment system under load or lock contention
	 */
	@ExceptionHandler(LockAcquisitionException.class)
	public ResponseEntity<ProblemDetail> handleLockTimeout(LockAcquisitionException ex) {
		log.warn("Lock acquisition failed: {}", ex.getMessage());
		return problem(HttpStatus.SERVICE_UNAVAILABLE, "lock-timeout",
				"Service Temporarily Unavailable",
				"Payment processing is temporarily unavailable. Please retry in a moment.");
	}

	/**
	 * Downstream service circuit breaker open
	 */
	@ExceptionHandler({AccountServiceWebClient.AccountServiceUnavailableException.class, FxServiceWebClient.FxServiceUnavailableException.class})
	public ResponseEntity<ProblemDetail> handleDependencyUnavailable(RuntimeException ex) {
		log.error("Dependency unavailable: {}", ex.getMessage());
		return problem(HttpStatus.SERVICE_UNAVAILABLE, "dependency-unavailable",
				"Dependency Unavailable",
				"A required service is currently unavailable. Please retry later.");
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
		log.warn("Access denied: {}", ex.getMessage());
		return problem(HttpStatus.FORBIDDEN, "access-denied", "Access Denied",
				"You do not have permission to perform this operation.");
	}

	/**
	 * Catch-all — never expose internals
	 */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
		log.error("Unexpected error — this should not happen, investigate immediately", ex);
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error",
				"Internal Server Error",
				"An unexpected error occurred. Contact support with the trace ID from the logs.");
	}

	private ResponseEntity<ProblemDetail> problem(HttpStatus status, String type,
	                                              String title, String detail) {
		ProblemDetail pd = ProblemDetail.forStatus(status);
		pd.setType(URI.create("https://fincore.bank.pl/errors/" + type));
		pd.setTitle(title);
		pd.setDetail(detail);
		pd.setProperty("timestamp", Instant.now().toString());
		return ResponseEntity.status(status).body(pd);
	}
}
