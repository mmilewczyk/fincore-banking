package com.matcodem.fincore.payment.domain.port.out;

import com.matcodem.fincore.payment.domain.model.Money;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

/**
 * Driven port - communication with Account Service.
 * Implementation uses REST (with circuit breaker) or Kafka commands.
 */
public interface AccountServiceClient {

	void debitAccount(String accountId, Money amount, String paymentReference);

	void creditAccount(String accountId, Money amount, String paymentReference);

	AccountInfo getAccountInfo(String accountId);

	record AccountInfo(
			String accountId,
			String currency,
			boolean active
	) {
	}
}
