package com.matcodem.fincore.fx.domain.port.out;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import com.matcodem.fincore.fx.domain.model.CurrencyPair;

/**
 * Driven port — fetches live rates from an external provider.
 * Implementations: ExchangeRatesApiClient, EcbRateClient, NbpRateClient.
 * Wrapped in circuit breaker + fallback chain at application layer.
 */
public interface RateProviderClient {

	/**
	 * Fetch rate for a single pair.
	 * Returns empty if pair not supported by this provider.
	 */
	Optional<RateQuote> fetchRate(CurrencyPair pair);

	/**
	 * Fetch all supported rates in bulk — more efficient than per-pair calls.
	 */
	Map<CurrencyPair, RateQuote> fetchAllRates();

	String getProviderName();

	int getPriority(); // lower = preferred

	record RateQuote(
			CurrencyPair pair,
			BigDecimal midRate,
			Instant timestamp,
			String providerName
	) {
	}
}
