package com.matcodem.fincore.fx.domain.service;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.matcodem.fincore.fx.domain.model.CurrencyPair;
import com.matcodem.fincore.fx.domain.model.ExchangeRate;
import com.matcodem.fincore.fx.domain.model.ExchangeRateId;
import com.matcodem.fincore.fx.domain.model.RateSource;
import com.matcodem.fincore.fx.domain.port.out.ExchangeRateRepository;
import com.matcodem.fincore.fx.domain.port.out.FxEventPublisher;
import com.matcodem.fincore.fx.domain.port.out.RateProviderClient;

import lombok.extern.slf4j.Slf4j;

/**
 * Domain Service - Rate Refresh with Provider Fallback Chain.
 * <p>
 * Provider priority (configured via getOrder()):
 * 1. ExchangeRatesApiClient (real-time, paid)
 * 2. EcbRateClient          (daily, free, ECB)
 * 3. NbpRateClient          (daily, free, PLN-only)
 * <p>
 * On each refresh attempt:
 * - Try provider 1 -> if circuit breaker open or exception, try provider 2 -> provider 3
 * - If all fail, the current cached rate remains active until it goes stale
 * - Stale rates cause conversions to fail with StaleRateException
 * <p>
 * Pure domain service - no Spring annotations.
 * Circuit breaker is applied at the infrastructure/adapter layer via @CircuitBreaker.
 */
@Slf4j
public class RateRefreshService {

	private final List<RateProviderClient> providers; // sorted by priority
	private final ExchangeRateRepository repository;
	private final FxEventPublisher eventPublisher;
	private final int defaultSpreadBps;
	private final Duration rateValidity;

	public RateRefreshService(List<RateProviderClient> providers,
	                          ExchangeRateRepository repository,
	                          FxEventPublisher eventPublisher,
	                          int defaultSpreadBps,
	                          Duration rateValidity) {
		this.providers = providers.stream()
				.sorted(Comparator.comparingInt(RateProviderClient::getPriority))
				.toList();
		this.repository = repository;
		this.eventPublisher = eventPublisher;
		this.defaultSpreadBps = defaultSpreadBps;
		this.rateValidity = rateValidity;

		log.info("RateRefreshService initialised with {} providers: {}",
				this.providers.size(),
				this.providers.stream().map(RateProviderClient::getProviderName).toList());
	}

	/**
	 * Refresh rate for a single pair - tries providers in priority order.
	 * Returns the new ExchangeRate, or empty if all providers fail.
	 */
	public Optional<ExchangeRate> refreshRate(CurrencyPair pair) {
		for (RateProviderClient provider : providers) {
			try {
				Optional<RateProviderClient.RateQuote> quote = provider.fetchRate(pair);
				if (quote.isPresent()) {
					ExchangeRate rate = storeNewRate(pair, quote.get());
					log.info("Rate refreshed for {} from {}: mid={}",
							pair, provider.getProviderName(), rate.getMidRate());
					return Optional.of(rate);
				}
			} catch (Exception ex) {
				log.warn("Provider {} failed for pair {}: {} - trying next",
						provider.getProviderName(), pair, ex.getMessage());
			}
		}

		log.error("All providers failed to fetch rate for {} - retaining existing rate", pair);
		return Optional.empty();
	}

	/**
	 * Bulk refresh - fetches all pairs from highest-priority provider,
	 * falls back per-pair for any gaps.
	 */
	public int refreshAllRates() {
		int refreshed = 0;

		for (RateProviderClient provider : providers) {
			try {
				Map<CurrencyPair, RateProviderClient.RateQuote> allQuotes = provider.fetchAllRates();
				for (var entry : allQuotes.entrySet()) {
					storeNewRate(entry.getKey(), entry.getValue());
					refreshed++;
				}
				log.info("Bulk refresh from {} - {} pairs updated", provider.getProviderName(), allQuotes.size());
				return refreshed; // success - no need to try further providers
			} catch (Exception ex) {
				log.warn("Bulk refresh from {} failed: {} - trying next provider",
						provider.getProviderName(), ex.getMessage());
			}
		}

		log.error("All providers failed for bulk refresh");
		return 0;
	}


	private ExchangeRate storeNewRate(CurrencyPair pair, RateProviderClient.RateQuote quote) {
		// Supersede any active rate for this pair first
		repository.findActiveByPair(pair).ifPresent(existing -> {
			ExchangeRateId placeholder = ExchangeRateId.generate();
			repository.supersedeAllForPair(pair, placeholder);
			var events = existing.pullDomainEvents();
			if (!events.isEmpty()) eventPublisher.publishAll(events);
		});

		ExchangeRate newRate = ExchangeRate.create(
				pair, quote.midRate(), defaultSpreadBps,
				resolveSource(quote.providerName()),
				quote.timestamp(), rateValidity
		);

		ExchangeRate saved = repository.save(newRate);
		eventPublisher.publishAll(saved.pullDomainEvents());
		return saved;
	}

	private RateSource resolveSource(String providerName) {
		return switch (providerName.toLowerCase()) {
			case "ecb" -> RateSource.ECB;
			case "exchangeratesapi" -> RateSource.EXCHANGE_RATES_API;
			case "nbp" -> RateSource.NBP;
			default -> RateSource.EXCHANGE_RATES_API;
		};
	}
}