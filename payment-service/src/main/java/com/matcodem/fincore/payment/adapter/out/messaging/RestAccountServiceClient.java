package com.matcodem.fincore.payment.adapter.out.messaging;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.matcodem.fincore.payment.domain.domain.port.out.AccountServiceClient;
import com.matcodem.fincore.payment.domain.model.Money;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST adapter for Account Service communication.
 * <p>
 * Wrapped with:
 * - @CircuitBreaker — opens circuit after 5 failures, half-opens after 10s
 * - @Retry — retries 3 times with exponential backoff before giving up
 * <p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RestAccountServiceClient implements AccountServiceClient {

	private final RestTemplate restTemplate;

	@Value("${services.account.base-url:http://localhost:8081}")
	private String accountServiceBaseUrl;

	private static final String SERVICE_NAME = "account-service";

	@Override
	@CircuitBreaker(name = SERVICE_NAME, fallbackMethod = "debitFallback")
	@Retry(name = SERVICE_NAME)
	public void debitAccount(String accountId, Money amount, String paymentReference) {
		log.debug("Debiting account {} by {} (ref: {})", accountId, amount, paymentReference);

		var request = Map.of(
				"amount", amount.getAmount(),
				"currency", amount.getCurrency().getCode(),
				"reference", paymentReference
		);

		restTemplate.exchange(
				accountServiceBaseUrl + "/api/v1/accounts/{accountId}/debit",
				HttpMethod.POST,
				new HttpEntity<>(request),
				Void.class,
				accountId
		);
	}

	@Override
	@CircuitBreaker(name = SERVICE_NAME, fallbackMethod = "creditFallback")
	@Retry(name = SERVICE_NAME)
	public void creditAccount(String accountId, Money amount, String paymentReference) {
		log.debug("Crediting account {} by {} (ref: {})", accountId, amount, paymentReference);

		var request = Map.of(
				"amount", amount.getAmount(),
				"currency", amount.getCurrency().getCode(),
				"reference", paymentReference
		);

		restTemplate.exchange(
				accountServiceBaseUrl + "/api/v1/accounts/{accountId}/credit",
				HttpMethod.POST,
				new HttpEntity<>(request),
				Void.class,
				accountId
		);
	}

	@Override
	@CircuitBreaker(name = SERVICE_NAME, fallbackMethod = "getAccountFallback")
	@Retry(name = SERVICE_NAME)
	public AccountInfo getAccountInfo(String accountId) {
		log.debug("Getting account info for: {}", accountId);

		var response = restTemplate.getForObject(
				accountServiceBaseUrl + "/api/v1/accounts/{accountId}",
				AccountInfoResponse.class,
				accountId
		);

		if (response == null) {
			throw new RuntimeException("Empty response from account service for: " + accountId);
		}

		return new AccountInfo(response.id(), response.currency(), "ACTIVE".equals(response.status()));
	}

	private void debitFallback(String accountId, Money amount, String reference, Exception ex) {
		log.error("Circuit breaker open — debit failed for account {}: {}", accountId, ex.getMessage());
		throw new AccountServiceUnavailableException("Account service unavailable for debit: " + accountId, ex);
	}

	private void creditFallback(String accountId, Money amount, String reference, Exception ex) {
		log.error("Circuit breaker open — credit failed for account {}: {}", accountId, ex.getMessage());
		throw new AccountServiceUnavailableException("Account service unavailable for credit: " + accountId, ex);
	}

	private AccountInfo getAccountFallback(String accountId, Exception ex) {
		log.error("Circuit breaker open — getAccountInfo failed for {}: {}", accountId, ex.getMessage());
		throw new AccountServiceUnavailableException("Account service unavailable for info: " + accountId, ex);
	}

	private record AccountInfoResponse(String id, String currency, String status) {
	}

	public static class AccountServiceUnavailableException extends RuntimeException {
		public AccountServiceUnavailableException(String message, Throwable cause) {
			super(message, cause);
		}
	}
}
