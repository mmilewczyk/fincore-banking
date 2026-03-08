package com.matcodem.fincore.payment.adapter.out.client;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.matcodem.fincore.payment.domain.model.Money;
import com.matcodem.fincore.payment.domain.port.out.AccountServiceClient;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;

/**
 * REST adapter to Account Service — implements AccountServiceClient port.
 * <p>
 * Uses the shared WebClient.Builder from InfrastructureConfig, which provides
 * Netty-level timeouts (connect 3s, read/write 5s). This prevents indefinite
 * blocking of Tomcat threads during account service outages.
 * <p>
 * Resilience4j:
 *
 * @CircuitBreaker — opens after 50% failure rate over sliding window of 10 calls.
 * OPEN state: calls fail-fast for 15s, no upstream requests sent.
 * @Retry — 3 attempts, 500ms exponential backoff, only on transient errors.
 * Does NOT retry on 4xx (account not found, insufficient funds) —
 * those are non-retryable business errors.
 * <p>
 * HTTP 404 from Account Service is treated as AccountServiceUnavailableException
 * rather than NoSuchElementException because the payment-service should not silently
 * proceed with debit/credit if the account is unexpectedly missing — that's an
 * infrastructure inconsistency, not a user error.
 */
@Slf4j
@Component
public class AccountServiceWebClient implements AccountServiceClient {

	private final WebClient webClient;

	public AccountServiceWebClient(
			WebClient.Builder webClientBuilder,
			@Value("${services.account.base-url:http://account-service.fincore.svc.cluster.local:8081}")
			String baseUrl) {
		this.webClient = webClientBuilder.baseUrl(baseUrl).build();
	}

	@Override
	@CircuitBreaker(name = "account-service", fallbackMethod = "debitFallback")
	@Retry(name = "account-service")
	public void debitAccount(String accountId, Money amount, String paymentReference) {
		log.info("Debiting account {} — amount: {}, ref: {}", accountId, amount, paymentReference);

		webClient.post()
				.uri("/api/v1/accounts/{id}/debit", accountId)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(Map.of(
						"amount", amount.getAmount(),
						"currency", amount.getCurrency().getCode(),
						"reference", paymentReference
				))
				.retrieve()
				.toBodilessEntity()
				.block();

		log.info("Debit OK — account: {}, ref: {}", accountId, paymentReference);
	}

	@Override
	@CircuitBreaker(name = "account-service", fallbackMethod = "creditFallback")
	@Retry(name = "account-service")
	public void creditAccount(String accountId, Money amount, String paymentReference) {
		log.info("Crediting account {} — amount: {}, ref: {}", accountId, amount, paymentReference);

		webClient.post()
				.uri("/api/v1/accounts/{id}/credit", accountId)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(Map.of(
						"amount", amount.getAmount(),
						"currency", amount.getCurrency().getCode(),
						"reference", paymentReference
				))
				.retrieve()
				.toBodilessEntity()
				.block();

		log.info("Credit OK — account: {}, ref: {}", accountId, paymentReference);
	}

	@Override
	@CircuitBreaker(name = "account-service", fallbackMethod = "getAccountFallback")
	@Retry(name = "account-service")
	public AccountInfo getAccountInfo(String accountId) {
		@SuppressWarnings("unchecked")
		Map<String, Object> response = webClient.get()
				.uri("/api/v1/accounts/{id}", accountId)
				.retrieve()
				.bodyToMono(Map.class)
				.block();

		if (response == null) {
			throw new AccountServiceUnavailableException("Empty response for account: " + accountId);
		}

		return new AccountInfo(
				(String) response.get("id"),
				(String) response.get("currency"),
				"ACTIVE".equals(response.get("status"))
		);
	}

	private void debitFallback(String accountId, Money amount, String ref, Throwable ex) {
		log.error("Account Service unavailable for debit on {}: {}", accountId, ex.getMessage());
		throw new AccountServiceUnavailableException("Account Service unavailable — debit: " + accountId);
	}

	private void creditFallback(String accountId, Money amount, String ref, Throwable ex) {
		log.error("Account Service unavailable for credit on {}: {}", accountId, ex.getMessage());
		throw new AccountServiceUnavailableException("Account Service unavailable — credit: " + accountId);
	}

	private AccountInfo getAccountFallback(String accountId, Throwable ex) {
		log.error("Account Service unavailable for getAccountInfo on {}: {}", accountId, ex.getMessage());
		throw new AccountServiceUnavailableException("Account Service unavailable — get: " + accountId);
	}

	public static class AccountServiceUnavailableException extends RuntimeException {
		public AccountServiceUnavailableException(String msg) {
			super(msg);
		}
	}
}