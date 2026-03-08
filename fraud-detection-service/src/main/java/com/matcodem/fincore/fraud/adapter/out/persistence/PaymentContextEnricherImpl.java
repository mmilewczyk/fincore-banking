package com.matcodem.fincore.fraud.adapter.out.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.matcodem.fincore.fraud.domain.model.PaymentContext;
import com.matcodem.fincore.fraud.domain.port.out.PaymentContextEnricher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Enriches raw payment data with:
 * <p>
 * 1. Account context - fetched from Account Service REST API
 * (active/frozen status, age, balance, fraud history)
 * <p>
 * 2. User behavioral context - computed from local transaction history
 * (velocity: tx count per hour/day, total amounts, largest tx ever)
 * <p>
 * Caching:
 * - Account info cached for 5 minutes (Redis) - reduces Account Service load
 * - Behavioral context is always fresh (computed from local DB)
 * <p>
 * Resilience:
 * - If Account Service is unavailable, falls back to minimal context
 * (rules that require account context will simply pass)
 * - Enrichment failure never blocks fraud analysis
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentContextEnricherImpl implements PaymentContextEnricher {

	private final RestTemplate restTemplate;
	private final TransactionHistoryRepository transactionHistoryRepository;

	@Value("${services.account.base-url:http://localhost:8081}")
	private String accountServiceBaseUrl;

	@Override
	public PaymentContext enrich(PaymentContext rawContext) {
		log.debug("Enriching payment context for payment: {}", rawContext.getPaymentId());

		PaymentContext.AccountContext sourceAccount = fetchAccountContext(rawContext.getSourceAccountId());
		PaymentContext.AccountContext targetAccount = fetchAccountContext(rawContext.getTargetAccountId());
		PaymentContext.UserBehaviorContext behavior = computeBehavior(
				rawContext.getInitiatedBy(), rawContext.getSourceAccountId()
		);

		return PaymentContext.builder()
				.paymentId(rawContext.getPaymentId())
				.idempotencyKey(rawContext.getIdempotencyKey())
				.sourceAccountId(rawContext.getSourceAccountId())
				.targetAccountId(rawContext.getTargetAccountId())
				.amount(rawContext.getAmount())
				.currency(rawContext.getCurrency())
				.paymentType(rawContext.getPaymentType())
				.initiatedBy(rawContext.getInitiatedBy())
				.initiatedAt(rawContext.getInitiatedAt())
				.sourceAccount(sourceAccount)
				.targetAccount(targetAccount)
				.userBehavior(behavior)
				.build();
	}

	@Cacheable(value = "account-context", key = "#accountId", unless = "#result == null")
	public PaymentContext.AccountContext fetchAccountContext(String accountId) {
		try {
			@SuppressWarnings("unchecked")
			Map<String, Object> response = restTemplate.getForObject(
					accountServiceBaseUrl + "/api/v1/accounts/{id}",
					Map.class, accountId
			);

			if (response == null) return minimalAccountContext(accountId);

			Instant createdAt = Instant.parse((String) response.getOrDefault("createdAt", Instant.now().toString()));
			boolean isNew = createdAt.isAfter(Instant.now().minus(30, ChronoUnit.DAYS));

			return new PaymentContext.AccountContext(
					accountId,
					(String) response.getOrDefault("ownerId", "unknown"),
					(String) response.getOrDefault("currency", "PLN"),
					new BigDecimal(response.getOrDefault("balance", "0").toString()),
					isNew,
					transactionHistoryRepository.countByAccountId(accountId),
					transactionHistoryRepository.averageAmountByAccountId(accountId),
					transactionHistoryRepository.hasFraudFlags(accountId)
			);
		} catch (Exception ex) {
			log.warn("Could not fetch account context for {} - using minimal fallback: {}",
					accountId, ex.getMessage());
			return minimalAccountContext(accountId);
		}
	}

	// ─── Behavioral context ───────────────────────────────────────

	private PaymentContext.UserBehaviorContext computeBehavior(String userId, String sourceAccountId) {
		try {
			Instant now = Instant.now();
			Instant oneHour = now.minus(1, ChronoUnit.HOURS);
			Instant oneDay = now.minus(24, ChronoUnit.HOURS);

			int txLastHour = transactionHistoryRepository.countSince(sourceAccountId, oneHour);
			int txLast24h = transactionHistoryRepository.countSince(sourceAccountId, oneDay);
			BigDecimal totalLast24h = transactionHistoryRepository.totalAmountSince(sourceAccountId, oneDay);
			BigDecimal largestEver = transactionHistoryRepository.largestTransactionEver(sourceAccountId);
			String usualCurrency = transactionHistoryRepository.mostFrequentCurrency(sourceAccountId);
			boolean hasIntl = transactionHistoryRepository.hasInternationalTransactions(sourceAccountId);

			return new PaymentContext.UserBehaviorContext(
					userId,
					txLastHour,
					txLast24h,
					totalLast24h,
					largestEver,
					usualCurrency != null ? usualCurrency : "PLN",
					hasIntl,
					new String[]{}
			);
		} catch (Exception ex) {
			log.warn("Could not compute behavioral context for user {} - using empty fallback: {}",
					userId, ex.getMessage());
			return emptyBehavior(userId);
		}
	}

	private PaymentContext.AccountContext minimalAccountContext(String accountId) {
		return new PaymentContext.AccountContext(
				accountId, "unknown", "PLN",
				BigDecimal.ZERO, false, 0, BigDecimal.ZERO, false
		);
	}

	private PaymentContext.UserBehaviorContext emptyBehavior(String userId) {
		return new PaymentContext.UserBehaviorContext(
				userId, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO,
				"PLN", false, new String[]{}
		);
	}
}
