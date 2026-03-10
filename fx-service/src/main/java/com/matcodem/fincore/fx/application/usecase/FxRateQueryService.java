package com.matcodem.fincore.fx.application.usecase;

import java.util.List;

import org.springframework.stereotype.Service;

import com.matcodem.fincore.fx.domain.model.CurrencyPair;
import com.matcodem.fincore.fx.domain.model.ExchangeRate;
import com.matcodem.fincore.fx.domain.port.in.GetExchangeRateUseCase;
import com.matcodem.fincore.fx.domain.port.out.ExchangeRateRepository;
import com.matcodem.fincore.fx.domain.service.RateRefreshService;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * FX Rate Query Service - handles rate retrieval with fallback logic.
 * <p>
 * Implements GetExchangeRateUseCase driven port.
 * <p>
 * Responsibilities:
 * - Single rate lookup (cache/DB)
 * - Single rate with provider fallback
 * - Get all active rates
 * - Rate freshness validation
 * <p>
 * Does NOT handle:
 * - Conversion math (delegate to ExchangeRate domain object)
 * - Event publishing (delegate to FxConversionService)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FxRateQueryService implements GetExchangeRateUseCase {

	private final ExchangeRateRepository rateRepository;
	private final RateRefreshService rateRefreshService;
	private final MeterRegistry meterRegistry;

	/**
	 * Get rate from cache/DB without fallback.
	 * Throws RateUnavailableException if no active rate exists.
	 */
	@Override
	@Timed(value = "fx.rate.get")
	public ExchangeRate getRate(CurrencyPair pair) {
		return rateRepository.findActiveByPair(pair)
				.filter(ExchangeRate::isActive)
				.orElseThrow(() -> {
					log.warn("No active rate in cache/DB for {}", pair);
					return new RateUnavailableException(
							"No active rate available for " + pair + ". Try getRateWithFallback()."
					);
				});
	}

	/**
	 * Get rate with live provider fallback if cache miss/stale.
	 * Used by conversion flow where freshest rate is critical.
	 * <p>
	 * Lookup order:
	 * 1. Cache (O(1), < 1ms)
	 * 2. DB (O(1), < 10ms)
	 * 3. Live providers via RateRefreshService (fallback chain)
	 *    - ExchangeRates API (primary)
	 *    - ECB (secondary, free EUR-based)
	 *    - Returns empty if all fail
	 */
	@Override
	@Timed(value = "fx.rate.get.with.fallback")
	public ExchangeRate getRateWithFallback(CurrencyPair pair) {
		// Check cache first
		var cached = rateRepository.findActiveByPair(pair);

		if (cached.isPresent() && cached.get().isActive()) {
			meterRegistry.counter("fx.rate.cache.hit", "pair", pair.getSymbol()).increment();
			log.debug("Rate cache hit for {}", pair);
			return cached.get();
		}

		// Cache miss - trigger live fetch
		meterRegistry.counter("fx.rate.cache.miss", "pair", pair.getSymbol()).increment();
		log.info("Rate cache miss for {} - fetching from provider chain", pair);

		return rateRefreshService.refreshRate(pair)
				.orElseThrow(() -> {
					log.error("All rate providers failed for {} - no fallback available", pair);
					return new RateUnavailableException(
							"All rate providers failed for " + pair + " - conversion rejected"
					);
				});
	}

	/**
	 * Get all currently active exchange rates.
	 * Used by UI to display available rates.
	 * <p>
	 * Rates from cache/DB only - no provider fallback.
	 */
	@Override
	@Timed(value = "fx.rates.get.all")
	public List<ExchangeRate> getAllActiveRates() {
		log.debug("Fetching all active rates from repository");

		List<ExchangeRate> rates = rateRepository.findAllActive();

		meterRegistry.gauge("fx.rates.active.count", rates.size());
		log.info("Fetched {} active rates", rates.size());

		return rates;
	}

	/**
	 * Rate unavailable exception - thrown when no rate can be found/fetched.
	 * Signals to conversion layer that operation should be rejected.
	 */
	public static class RateUnavailableException extends RuntimeException {
		public RateUnavailableException(String message) {
			super(message);
		}
	}
}




