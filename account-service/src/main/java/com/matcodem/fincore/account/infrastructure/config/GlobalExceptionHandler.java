package com.matcodem.fincore.account.infrastructure.config;

import java.net.URI;
import java.time.Instant;
import java.util.NoSuchElementException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.matcodem.fincore.account.domain.model.AccountNotActiveException;
import com.matcodem.fincore.account.domain.model.CurrencyMismatchException;
import com.matcodem.fincore.account.domain.model.InsufficientFundsException;
import com.matcodem.fincore.account.domain.model.InvalidIBANException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(NoSuchElementException.class)
	public ProblemDetail handleNotFound(NoSuchElementException ex) {
		var problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
		problem.setType(URI.create("https://fincore.com/errors/not-found"));
		problem.setProperty("timestamp", Instant.now());
		return problem;
	}

	@ExceptionHandler(InsufficientFundsException.class)
	public ProblemDetail handleInsufficientFunds(InsufficientFundsException ex) {
		var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
		problem.setType(URI.create("https://fincore.com/errors/insufficient-funds"));
		problem.setProperty("timestamp", Instant.now());
		return problem;
	}

	@ExceptionHandler(AccountNotActiveException.class)
	public ProblemDetail handleAccountNotActive(AccountNotActiveException ex) {
		var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
		problem.setType(URI.create("https://fincore.com/errors/account-not-active"));
		problem.setProperty("timestamp", Instant.now());
		return problem;
	}

	@ExceptionHandler(CurrencyMismatchException.class)
	public ProblemDetail handleCurrencyMismatch(CurrencyMismatchException ex) {
		var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
		problem.setType(URI.create("https://fincore.com/errors/currency-mismatch"));
		problem.setProperty("timestamp", Instant.now());
		return problem;
	}

	@ExceptionHandler(InvalidIBANException.class)
	public ProblemDetail handleInvalidIBAN(InvalidIBANException ex) {
		var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
		problem.setType(URI.create("https://fincore.com/errors/invalid-iban"));
		problem.setProperty("timestamp", Instant.now());
		return problem;
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
		var problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
		problem.setType(URI.create("https://fincore.com/errors/validation"));
		problem.setDetail("Validation failed");
		problem.setProperty("timestamp", Instant.now());
		problem.setProperty("errors", ex.getBindingResult().getFieldErrors().stream()
				.map(e -> e.getField() + ": " + e.getDefaultMessage())
				.toList());
		return problem;
	}

	@ExceptionHandler(Exception.class)
	public ProblemDetail handleGeneric(Exception ex) {
		log.error("Unhandled exception", ex);
		var problem = ProblemDetail.forStatusAndDetail(
				HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
		problem.setType(URI.create("https://fincore.com/errors/internal"));
		problem.setProperty("timestamp", Instant.now());
		return problem;
	}
}