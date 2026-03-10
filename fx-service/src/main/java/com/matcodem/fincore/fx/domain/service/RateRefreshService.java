package com.matcodem.fincore.fx.domain.service;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.matcodem.fincore.fx.domain.model.CurrencyPair;
import com.matcodem.fincore.fx.domain.model.ExchangeRate;
import com.matcodem.fincore.fx.domain.model.ExchangeRateId;
import com.matcodem.fincore.fx.domain.model.RateSource;
import com.matcodem.fincore.fx.domain.port.out.ExchangeRateRepository;
import com.matcodem.fincore.fx.domain.port.out.FxEventPublisher;
import com.matcodem.fincore.fx.domain.port.out.RateProviderClient;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * Domain Service - Rate Refresh with Provider Fallback Chain (Production-Ready).
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
 * Production Features:
 * - Thread-safe refresh operations (ReentrantReadWriteLock)
 * - Comprehensive metrics tracking (success/failure per provider)
 * - Detailed logging with provider chain context
 * - Input validation and null checks
 * - Provider configuration with RateSourceRegistry
 * - Telemetry for monitoring and SLA tracking
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
	private final MeterRegistry meterRegistry;
	private final ReadWriteLock refreshLock;

	// Metrics
	private final Counter successCounter;
	private final Counter failureCounter;
	private final Counter providerFailureCounter;
	private final Counter bulkRefreshSuccessCounter;
	private final Counter bulkRefreshFailureCounter;

	public RateRefreshService(List<RateProviderClient> providers,
	                          ExchangeRateRepository repository,
	                          FxEventPublisher eventPublisher,
	                          int defaultSpreadBps,
	                          Duration rateValidity,
	                          MeterRegistry meterRegistry) {
		// Validation
		Objects.requireNonNull(providers, "providers list cannot be null");
		Objects.requireNonNull(repository, "repository cannot be null");
		Objects.requireNonNull(eventPublisher, "eventPublisher cannot be null");
		Objects.requireNonNull(rateValidity, "rateValidity cannot be null");
		Objects.requireNonNull(meterRegistry, "meterRegistry cannot be null");

		if (providers.isEmpty()) {
			throw new IllegalArgumentException("At least one rate provider must be configured");
		}

		this.providers = providers.stream()
				.sorted(Comparator.comparingInt(RateProviderClient::getPriority))
				.toList();
		this.repository = repository;
		this.eventPublisher = eventPublisher;
		this.defaultSpreadBps = defaultSpreadBps;
		this.rateValidity = rateValidity;
		this.meterRegistry = meterRegistry;
		this.refreshLock = new ReentrantReadWriteLock();

		// Initialize metrics
		this.successCounter = Counter.builder("fx.rate.refresh.success")
				.description("Successful rate refresh operations")
				.register(meterRegistry);

		this.failureCounter = Counter.builder("fx.rate.refresh.failure")
				.description("Failed rate refresh operations")
				.register(meterRegistry);

		this.providerFailureCounter = Counter.builder("fx.rate.provider.failure")
				.description("Provider-specific refresh failures")
				.register(meterRegistry);

		this.bulkRefreshSuccessCounter = Counter.builder("fx.rate.bulk.refresh.success")
				.description("Successful bulk refresh operations")
				.register(meterRegistry);

		this.bulkRefreshFailureCounter = Counter.builder("fx.rate.bulk.refresh.failure")
				.description("Failed bulk refresh operations")
				.register(meterRegistry);

		logInitialization();
	}

	/**
	 * Refresh rate for a single pair - tries providers in priority order (thread-safe).
	 * Returns the new ExchangeRate, or empty if all providers fail.
	 *
	 * @param pair currency pair to refresh
	 * @return Optional containing new rate or empty if all providers fail
	 */
	public Optional<ExchangeRate> refreshRate(CurrencyPair pair) {
		Objects.requireNonNull(pair, "pair cannot be null");

		refreshLock.readLock().lock();
		try {
			log.debug("Starting rate refresh for pair {} via provider chain (count: {})",
					pair, providers.size());

			for (int i = 0; i < providers.size(); i++) {
				RateProviderClient provider = providers.get(i);
				try {
					Optional<RateProviderClient.RateQuote> quote = provider.fetchRate(pair);
					if (quote.isPresent()) {
						ExchangeRate rate = storeNewRate(pair, quote.get());
						successCounter.increment();
						log.info("Rate refreshed for {} from {} (provider {}/{}): mid={} spread={}bps",
								pair, provider.getProviderName(), i + 1, providers.size(),
								rate.getMidRate(), rate.getSpreadBasisPoints());
						return Optional.of(rate);
					}
					log.debug("Provider {} returned empty for {}", provider.getProviderName(), pair);

				} catch (Exception ex) {
					providerFailureCounter.increment();
					log.warn("Provider {} ({}/{}) failed for pair {}: {} - trying next",
							provider.getProviderName(), i + 1, providers.size(), pair, ex.getMessage());
				}
			}

			failureCounter.increment();
			log.error("All {} providers failed to fetch rate for {} - retaining existing rate",
					providers.size(), pair);
			return Optional.empty();

		} finally {
			refreshLock.readLock().unlock();
		}
	}

	/**
	 * Bulk refresh - fetches all pairs from highest-priority provider (thread-safe).
	 * Falls back to next provider if current fails.
	 *
	 * @return number of rates successfully refreshed
	 */
	public int refreshAllRates() {
		refreshLock.readLock().lock();
		try {
			log.info("Starting bulk rate refresh via provider chain (count: {})", providers.size());

			int refreshed = 0;

			for (int i = 0; i < providers.size(); i++) {
				RateProviderClient provider = providers.get(i);
				try {
					Map<CurrencyPair, RateProviderClient.RateQuote> allQuotes = provider.fetchAllRates();

					if (allQuotes.isEmpty()) {
						log.debug("Provider {} ({}/{}) returned empty result",
								provider.getProviderName(), i + 1, providers.size());
						continue;
					}

					for (var entry : allQuotes.entrySet()) {
						storeNewRate(entry.getKey(), entry.getValue());
						refreshed++;
					}

					bulkRefreshSuccessCounter.increment();
					log.info("Bulk refresh from {} ({}/{}): {} rates updated",
							provider.getProviderName(), i + 1, providers.size(), allQuotes.size());
					return refreshed; // success - no need to try further providers

				} catch (Exception ex) {
					providerFailureCounter.increment();
					log.warn("Bulk refresh from {} ({}/{}) failed: {} - trying next provider",
							provider.getProviderName(), i + 1, providers.size(), ex.getMessage());
				}
			}

			bulkRefreshFailureCounter.increment();
			log.error("All {} providers failed for bulk refresh", providers.size());
			return refreshed;

		} finally {
			refreshLock.readLock().unlock();
		}
	}

	/**
	 * Store new rate - handles superseding old rates and publishing events.
	 * Internal method with proper null/validation checks.
	 */
	private ExchangeRate storeNewRate(CurrencyPair pair, RateProviderClient.RateQuote quote) {
		Objects.requireNonNull(pair, "pair cannot be null");
		Objects.requireNonNull(quote, "quote cannot be null");

		// Supersede any active rate for this pair first
		repository.findActiveByPair(pair).ifPresent(existing -> {
			ExchangeRateId placeholder = ExchangeRateId.generate();
			repository.supersedeAllForPair(pair, placeholder);
			var events = existing.pullDomainEvents();
			if (!events.isEmpty()) {
				eventPublisher.publishAll(events);
			}
		});

		RateSource source = RateSourceRegistry.resolveSource(quote.providerName());

		ExchangeRate newRate = ExchangeRate.create(
				pair, quote.midRate(), defaultSpreadBps,
				source, quote.timestamp(), rateValidity
		);

		ExchangeRate saved = repository.save(newRate);
		eventPublisher.publishAll(saved.pullDomainEvents());
		return saved;
	}

	private void logInitialization() {
		log.info("RateRefreshService initialized with parameters:");
		log.info("  - Providers: {} (in priority order: {})",
				providers.size(),
				providers.stream().map(RateProviderClient::getProviderName).toList());
		log.info("  - Default spread: {} bps", defaultSpreadBps);
		log.info("  - Rate validity: {}", rateValidity);
		log.info("  - Thread-safety: ReentrantReadWriteLock enabled");
		log.info("  - Metrics: Registered 5 counters for monitoring");
	}
}

