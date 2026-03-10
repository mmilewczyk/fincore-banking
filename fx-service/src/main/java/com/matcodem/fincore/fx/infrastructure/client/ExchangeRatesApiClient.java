package com.matcodem.fincore.fx.infrastructure.client;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.matcodem.fincore.fx.domain.model.Currency;
import com.matcodem.fincore.fx.domain.model.CurrencyPair;
import com.matcodem.fincore.fx.domain.port.out.RateProviderClient;
import com.matcodem.fincore.fx.infrastructure.client.dto.ExchangeRatesApiResponse;
import com.matcodem.fincore.fx.infrastructure.client.exception.RateProviderException;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * Primary rate provider - exchangeratesapi.io (enterprise-grade implementation).
 * <p>
 * API: GET /latest?base=EUR&symbols=PLN,USD,GBP,...
 * Response: { "base": "EUR", "date": "2024-03-06", "rates": { "PLN": 4.285, ... } }
 * <p>
 * Features:
 * - Uses RestClient (modern, synchronous, enterprise-friendly)
 * - Type-safe DTOs instead of raw Map<String, Object>
 * - Circuit Breaker protection - opens after 5 failures within 10s window
 * - Structured logging and metrics tracking
 * - Comprehensive error handling with custom exceptions
 * - Follows enterprise patterns: DI, AOP, observability
 */
@Slf4j
@Component
@SuppressWarnings("unused") // Methods like fetchRateFallback are called via Resilience4j reflection
public class ExchangeRatesApiClient implements RateProviderClient {

	private static final String PROVIDER_NAME = "exchangeratesapi";
	private static final String BASE_URL = "https://api.exchangeratesapi.io/v1";

	private final RestClient restClient;
	private final String apiKey;
	private final String baseCurrency;

	// Metrics
	private final Counter successCounter;
	private final Counter failureCounter;

	public ExchangeRatesApiClient(
			RestClient rateProviderRestClient,
			MeterRegistry meterRegistry,
			@Value("${fx.providers.exchange-rates-api.api-key:demo}") String apiKey,
			@Value("${fx.providers.exchange-rates-api.base-currency:EUR}") String baseCurrency) {
		this.restClient = rateProviderRestClient;
		this.apiKey = apiKey;
		this.baseCurrency = baseCurrency;

		// Register metrics
		this.successCounter = Counter.builder("fx.rate.provider.requests")
				.tag("provider", PROVIDER_NAME)
				.tag("status", "success")
				.register(meterRegistry);

		this.failureCounter = Counter.builder("fx.rate.provider.requests")
				.tag("provider", PROVIDER_NAME)
				.tag("status", "failure")
				.register(meterRegistry);

		log.info("ExchangeRatesApiClient initialized with base URL: {}", BASE_URL);
	}

	@Override
	@CircuitBreaker(name = "exchange-rates-api", fallbackMethod = "fetchRateFallback")
	@Retry(name = "exchange-rates-api")
	public Optional<RateQuote> fetchRate(CurrencyPair pair) {
		try {
			log.debug("Fetching rate for pair: {} from {}", pair, PROVIDER_NAME);

			ExchangeRatesApiResponse response = restClient.get()
					.uri(uriBuilder -> uriBuilder
							.scheme("https")
							.host("api.exchangeratesapi.io")
							.path("/v1/latest")
							.queryParam("access_key", apiKey)
							.queryParam("base", pair.getBase().getCode())
							.queryParam("symbols", pair.getQuote().getCode())
							.build())
					.retrieve()
					.onStatus(HttpStatusCode::isError, (request, clientResponse) -> {
						log.warn("API error for pair {}: {} {}", pair,
								clientResponse.getStatusCode(), clientResponse.getStatusText());
						throw new RateProviderException(
								PROVIDER_NAME,
								"Failed to fetch rate: " + clientResponse.getStatusText(),
								clientResponse.getStatusCode().value()
						);
					})
					.body(ExchangeRatesApiResponse.class);

			if (response == null) {
				log.warn("Empty response from {} for pair {}", PROVIDER_NAME, pair);
				failureCounter.increment();
				return Optional.empty();
			}

			if (!response.isSuccessful()) {
				log.warn("API returned error for pair {}: {}", pair, response.getError());
				failureCounter.increment();
				return Optional.empty();
			}

			Optional<RateQuote> result = parseRate(response, pair);
			if (result.isPresent()) {
				successCounter.increment();
				log.debug("Successfully fetched rate for {}: {}", pair, result.get().midRate());
			} else {
				failureCounter.increment();
			}
			return result;

		} catch (HttpClientErrorException | HttpServerErrorException ex) {
			log.error("HTTP error fetching rate for {}: {} {}", pair, ex.getStatusCode(), ex.getMessage());
			failureCounter.increment();
			throw new RateProviderException(PROVIDER_NAME,
					"HTTP error: " + ex.getMessage(), ex, ex.getStatusCode().value());
		} catch (RestClientException ex) {
			log.error("RestClient error fetching rate for {}: {}", pair, ex.getMessage(), ex);
			failureCounter.increment();
			throw new RateProviderException(PROVIDER_NAME,
					"Connection error: " + ex.getMessage(), ex);
		}
	}

	@Override
	@CircuitBreaker(name = "exchange-rates-api", fallbackMethod = "fetchAllRatesFallback")
	@Retry(name = "exchange-rates-api")
	public Map<CurrencyPair, RateQuote> fetchAllRates() {
		try {
			log.debug("Fetching all rates from {}", PROVIDER_NAME);

			ExchangeRatesApiResponse response = restClient.get()
					.uri(uriBuilder -> uriBuilder
							.scheme("https")
							.host("api.exchangeratesapi.io")
							.path("/v1/latest")
							.queryParam("access_key", apiKey)
							.queryParam("base", baseCurrency)
							.build())
					.retrieve()
					.onStatus(HttpStatusCode::isError, (request, clientResponse) -> {
						log.warn("API error for bulk fetch: {} {}",
								clientResponse.getStatusCode(), clientResponse.getStatusText());
						throw new RateProviderException(
								PROVIDER_NAME,
								"Failed to fetch rates: " + clientResponse.getStatusText(),
								clientResponse.getStatusCode().value()
						);
					})
					.body(ExchangeRatesApiResponse.class);

			if (response == null) {
				log.warn("Empty response from {} for bulk fetch", PROVIDER_NAME);
				failureCounter.increment();
				return Map.of();
			}

			if (!response.isSuccessful()) {
				log.warn("API returned error for bulk fetch: {}", response.getError());
				failureCounter.increment();
				return Map.of();
			}

			Map<CurrencyPair, RateQuote> result = parseAllRates(response);
			successCounter.increment(result.size());

			log.info("Successfully fetched {} rates from {}", result.size(), PROVIDER_NAME);
			return result;

		} catch (HttpClientErrorException | HttpServerErrorException ex) {
			log.error("HTTP error fetching all rates: {} {}", ex.getStatusCode(), ex.getMessage());
			failureCounter.increment();
			throw new RateProviderException(PROVIDER_NAME,
					"HTTP error: " + ex.getMessage(), ex, ex.getStatusCode().value());
		} catch (RestClientException ex) {
			log.error("RestClient error fetching all rates: {}", ex.getMessage(), ex);
			failureCounter.increment();
			throw new RateProviderException(PROVIDER_NAME,
					"Connection error: " + ex.getMessage(), ex);
		}
	}

	private Optional<RateQuote> parseRate(ExchangeRatesApiResponse response, CurrencyPair pair) {
		if (response == null || response.getRates() == null) {
			return Optional.empty();
		}

		Number rateValue = response.getRates().get(pair.getQuote().getCode());
		if (rateValue == null) {
			return Optional.empty();
		}

		try {
			BigDecimal rate = new BigDecimal(rateValue.toString());
			return Optional.of(new RateQuote(
					pair, rate, Instant.now(), getProviderName()
			));
		} catch (NumberFormatException ex) {
			log.warn("Invalid rate value for {}: {}", pair, rateValue);
			return Optional.empty();
		}
	}

	private Map<CurrencyPair, RateQuote> parseAllRates(ExchangeRatesApiResponse response) {
		Map<CurrencyPair, RateQuote> result = new HashMap<>();

		if (response == null || response.getRates() == null || response.getBase() == null) {
			return result;
		}

		try {
			Currency base = Currency.fromCode(response.getBase());
			Instant timestamp = response.getTimestamp() != null ?
					Instant.ofEpochSecond(response.getTimestamp()) : Instant.now();

			for (var entry : response.getRates().entrySet()) {
				try {
					Currency quote = Currency.fromCode(entry.getKey());
					if (base == quote) {
						continue;
					}

					CurrencyPair pair = CurrencyPair.of(base, quote);
					BigDecimal rate = new BigDecimal(entry.getValue().toString());
					result.put(pair, new RateQuote(pair, rate, timestamp, getProviderName()));
				} catch (NumberFormatException ex) {
					log.warn("Invalid rate value for currency {}: {}", entry.getKey(), entry.getValue());
				} catch (IllegalArgumentException ex) {
					log.debug("Skipping unsupported currency: {}", entry.getKey());
				}
			}
		} catch (IllegalArgumentException ex) {
			log.warn("Invalid base currency: {}", response.getBase());
		}

		return result;
	}


	private Optional<RateQuote> fetchRateFallback(CurrencyPair pair, Exception ex) {
		log.warn("ExchangeRatesApi circuit breaker open for pair {}: {}", pair, ex.getMessage());
		return Optional.empty(); // RateRefreshService will try next provider
	}

	private Map<CurrencyPair, RateQuote> fetchAllRatesFallback(Exception ex) {
		log.warn("ExchangeRatesApi bulk fetch failed: {}", ex.getMessage());
		return Map.of();
	}

	@Override
	public String getProviderName() {
		return "exchangeratesapi";
	}

	@Override
	public int getPriority() {
		return 1;
	}
}