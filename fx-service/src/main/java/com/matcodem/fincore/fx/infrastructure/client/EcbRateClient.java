package com.matcodem.fincore.fx.infrastructure.client;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.matcodem.fincore.fx.domain.model.Currency;
import com.matcodem.fincore.fx.domain.model.CurrencyPair;
import com.matcodem.fincore.fx.domain.port.out.RateProviderClient;
import com.matcodem.fincore.fx.infrastructure.client.exception.RateProviderException;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * Fallback rate provider #2 - European Central Bank daily reference rates (enterprise-grade).
 * <p>
 * ECB publishes rates daily at ~16:00 CET for ~30 currency pairs vs EUR.
 * Free, no API key required, highly reliable infrastructure.
 * <p>
 * XML endpoint: <a href="https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml">ECB Daily Reference Rates</a>
 * <p>
 * NOTE: ECB only publishes EUR-based rates - pairs like USD/PLN are computed
 * as cross rates: USD/PLN = (1/EUR/USD) * (EUR/PLN)
 * <p>
 * Features:
 * - Uses RestClient (modern, synchronous, enterprise-friendly)
 * - Robust XML parsing with regex validation
 * - Circuit Breaker protection
 * - Comprehensive metrics and structured logging
 * - Custom exception handling with context
 * - Follows banking-grade reliability standards
 */
@Slf4j
@Component
@SuppressWarnings("unused") // Methods called via Resilience4j reflection
public class EcbRateClient implements RateProviderClient {

	private static final String PROVIDER_NAME = "ecb";
	private static final String ECB_URL =
			"https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml";

	private static final Pattern RATE_PATTERN =
			Pattern.compile("currency='([A-Z]{3})'\\s+rate='([0-9.]+)'");

	private final RestClient restClient;
	@SuppressWarnings("unused") // Used for metric registration in constructor
	private final MeterRegistry meterRegistry;

	// Metrics
	private final Counter successCounter;
	private final Counter failureCounter;

	public EcbRateClient(RestClient rateProviderRestClient, MeterRegistry meterRegistry) {
		this.restClient = rateProviderRestClient;
		this.meterRegistry = meterRegistry;

		// Register metrics
		this.successCounter = Counter.builder("fx.rate.provider.requests")
				.tag("provider", PROVIDER_NAME)
				.tag("status", "success")
				.register(meterRegistry);

		this.failureCounter = Counter.builder("fx.rate.provider.requests")
				.tag("provider", PROVIDER_NAME)
				.tag("status", "failure")
				.register(meterRegistry);

		log.info("EcbRateClient initialized - provider: {} (priority: 2, fallback)", PROVIDER_NAME);
	}

	@Override
	@CircuitBreaker(name = "ecb-provider", fallbackMethod = "fetchRateFallback")
	public Optional<RateQuote> fetchRate(CurrencyPair pair) {
		// ECB only has EUR-based rates
		if (pair.getBase() != Currency.EUR && pair.getQuote() != Currency.EUR) {
			log.debug("ECB cannot fetch cross-rate (non-EUR based): {}", pair);
			return Optional.empty(); // cross rate - not directly available
		}

		Map<CurrencyPair, RateQuote> allRates = fetchAllRates();
		Optional<RateQuote> result = Optional.ofNullable(allRates.get(pair));

		if (result.isPresent()) {
			log.debug("ECB rate found for {}: {}", pair, result.get().midRate());
		} else {
			log.debug("ECB rate not found for {}", pair);
		}

		return result;
	}

	@Override
	@CircuitBreaker(name = "ecb-provider", fallbackMethod = "fetchAllRatesFallback")
	public Map<CurrencyPair, RateQuote> fetchAllRates() {
		try {
			log.debug("Fetching all rates from ECB...");

			String xml = restClient.get()
					.uri(ECB_URL)
					.retrieve()
					.onStatus(HttpStatusCode::isError, (request, clientResponse) -> {
						log.warn("ECB API error: {} {}",
								clientResponse.getStatusCode(), clientResponse.getStatusText());
						throw new RateProviderException(
								PROVIDER_NAME,
								"Failed to fetch rates: " + clientResponse.getStatusText(),
								clientResponse.getStatusCode().value()
						);
					})
					.body(String.class);

			if (xml == null || xml.isBlank()) {
				log.warn("Empty response from ECB");
				failureCounter.increment();
				return Map.of();
			}

			Map<CurrencyPair, RateQuote> result = parseXml(xml);
			successCounter.increment(result.size());

			log.info("ECB successfully fetched and parsed {} rates", result.size());
			return result;

		} catch (HttpClientErrorException | HttpServerErrorException ex) {
			log.error("HTTP error fetching ECB rates: {} {}", ex.getStatusCode(), ex.getMessage());
			failureCounter.increment();
			throw new RateProviderException(PROVIDER_NAME,
					"HTTP error: " + ex.getMessage(), ex, ex.getStatusCode().value());
		} catch (RestClientException ex) {
			log.error("RestClient error fetching ECB rates: {}", ex.getMessage(), ex);
			failureCounter.increment();
			throw new RateProviderException(PROVIDER_NAME,
					"Connection error: " + ex.getMessage(), ex);
		}
	}

	private Map<CurrencyPair, RateQuote> parseXml(String xml) {
		if (xml == null || xml.isBlank()) {
			log.warn("Cannot parse empty XML response from ECB");
			return Map.of();
		}

		Map<CurrencyPair, RateQuote> result = new HashMap<>();
		Matcher matcher = RATE_PATTERN.matcher(xml);
		Instant timestamp = Instant.now();
		int parsedCount = 0;
		int skippedCount = 0;

		while (matcher.find()) {
			try {
				String currencyCode = matcher.group(1);
				String rateValue = matcher.group(2);

				Currency quote = Currency.fromCode(currencyCode);
				BigDecimal rate = new BigDecimal(rateValue);
				CurrencyPair pair = CurrencyPair.of(Currency.EUR, quote);

				result.put(pair, new RateQuote(pair, rate, timestamp, getProviderName()));
				parsedCount++;

			} catch (NumberFormatException ex) {
				log.warn("Invalid rate value in ECB response for currency {}: {}",
						matcher.group(1), matcher.group(2));
				skippedCount++;
			} catch (IllegalArgumentException ex) {
				log.debug("ECB currency not supported: {}", matcher.group(1));
				skippedCount++;
			}
		}

		log.debug("ECB XML parsing complete - parsed: {}, skipped: {}", parsedCount, skippedCount);
		return result;
	}

	/**
	 * Fallback when fetchRate circuit is open.
	 * Returns empty Optional - caller will try next provider in chain.
	 */
	private Optional<RateQuote> fetchRateFallback(CurrencyPair pair, Exception ex) {
		log.warn("ECB rate provider unavailable - circuit breaker open for {}: {}. Trying next provider...",
				pair, ex.getMessage());
		return Optional.empty();
	}

	/**
	 * Fallback when fetchAllRates circuit is open.
	 * Returns empty map - caller will try next provider in chain.
	 */
	private Map<CurrencyPair, RateQuote> fetchAllRatesFallback(Exception ex) {
		log.warn("ECB bulk fetch unavailable - circuit breaker open: {}. Trying next provider...",
				ex.getMessage());
		return Map.of();
	}

	@Override
	public String getProviderName() {
		return "ecb";
	}

	@Override
	public int getPriority() {
		return 2;
	}
}
