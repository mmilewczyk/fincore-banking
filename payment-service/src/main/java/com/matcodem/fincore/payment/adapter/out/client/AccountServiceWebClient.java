package com.matcodem.fincore.payment.adapter.out.client;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.matcodem.fincore.payment.domain.model.Money;
import com.matcodem.fincore.payment.domain.port.out.AccountServiceClient;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;

/**
 * Feign-style WebClient adapter to Account Service.
 * <p>
 * Uses K8s DNS: http://account-service.fincore.svc.cluster.local:8081
 * <p>
 * Circuit Breaker (Resilience4j):
 * - 5 failures in 10s sliding window → OPEN
 * - 30s wait → HALF_OPEN → 3 test calls
 * - Fallback: throw AccountServiceUnavailableException
 * → Payment Service marks payment FAILED and publishes event
 * <p>
 * Synchronous because debit/credit need acknowledgement before
 * the payment can be marked COMPLETED. Money must move atomically.
 */
@Slf4j
@Component
public class AccountServiceWebClient implements AccountServiceClient {

	private final WebClient webClient;

	private static final String SERVICE_NAME = "account-service";

	public AccountServiceWebClient(
			WebClient.Builder builder,
			@Value("${services.account.base-url:http://account-service.fincore.svc.cluster.local:8081}")
			String baseUrl) {
		this.webClient = builder.baseUrl(baseUrl).build();
	}

	@Override
	@CircuitBreaker(name = SERVICE_NAME, fallbackMethod = "debitFallback")
	public void debitAccount(String accountId, Money amount, String reference) {
		log.info("Debiting account {} - amount: {}, ref: {}", accountId, amount.toString(), reference);

		webClient.post()
				.uri("/api/v1/accounts/{id}/debit", accountId)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(Map.of(
						"amount", amount.getAmount(),
						"currency", amount.getCurrency().getCode(),
						"reference", reference
				))
				.retrieve()
				.toBodilessEntity()
				.block();

		log.info("Debit successful - account: {}, ref: {}", accountId, reference);
	}

	@Override
	@CircuitBreaker(name = SERVICE_NAME, fallbackMethod = "creditFallback")
	public void creditAccount(String accountId, Money amount, String reference) {
		log.info("Crediting account {} - amount: {}, ref: {}", accountId, amount, reference);

		webClient.post()
				.uri("/api/v1/accounts/{id}/credit", accountId)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(Map.of(
						"amount", amount.getAmount(),
						"currency", amount.getCurrency().getCode(),
						"reference", reference
				))
				.retrieve()
				.toBodilessEntity()
				.block();

		log.info("Credit successful — account: {}, ref: {}", accountId, reference);
	}

	@CircuitBreaker(name = SERVICE_NAME, fallbackMethod = "getAccountFallback")
	@Override
	public AccountInfo getAccountInfo(String accountId) {
		var response = webClient.get()
				.uri("/api/v1/accounts/{id}", accountId)
				.retrieve()
				.bodyToMono(Map.class)
				.block();

		if (response == null) throw new AccountServiceUnavailableException("Empty response for account: " + accountId);

		return new AccountServiceClient.AccountInfo(
				(String) response.get("id"),
				(String) response.get("currency"),
				(Boolean) response.get("status")
		);
	}

	private void debitFallback(String accountId, BigDecimal amount, String currency,
	                           String reference, Exception ex) {
		log.error("Account Service unavailable — debit failed for account {}: {}", accountId, ex.getMessage());
		throw new AccountServiceUnavailableException(
				"Account Service unavailable — debit failed for account: " + accountId);
	}

	private void creditFallback(String accountId, BigDecimal amount, String currency,
	                            String reference, Exception ex) {
		log.error("Account Service unavailable — credit failed for account {}: {}", accountId, ex.getMessage());
		throw new AccountServiceUnavailableException(
				"Account Service unavailable — credit failed for account: " + accountId);
	}

	private AccountInfo getAccountFallback(String accountId, Exception ex) {
		log.error("Account Service unavailable — cannot fetch account {}: {}", accountId, ex.getMessage());
		throw new AccountServiceUnavailableException(
				"Account Service unavailable — cannot fetch account: " + accountId);
	}

	public static class AccountServiceUnavailableException extends RuntimeException {
		public AccountServiceUnavailableException(String msg) {
			super(msg);
		}
	}
}