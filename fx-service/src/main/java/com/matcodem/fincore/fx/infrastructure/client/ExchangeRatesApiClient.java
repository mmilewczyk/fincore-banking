package com.matcodem.fincore.fx.infrastructure.client;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.matcodem.fincore.fx.domain.model.Currency;
import com.matcodem.fincore.fx.domain.model.CurrencyPair;
import com.matcodem.fincore.fx.domain.port.out.RateProviderClient;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;

/**
 * Primary rate provider — exchangeratesapi.io.
 * <p>
 * API: GET /latest?base=EUR&symbols=PLN,USD,GBP,...
 * Response: { "base": "EUR", "date": "2024-03-06", "rates": { "PLN": 4.285, ... } }
 * <p>
 * Circuit Breaker wraps all calls — opens after 5 failures within 10s window.
 * When open, falls back to EcbRateClient automatically.
 * <p>
 * Uses WebClient (non-blocking) — rate fetches don't block virtual threads.
 */
@Slf4j
@Component
public class ExchangeRatesApiClient implements RateProviderClient {

	private final WebClient webClient;

	@Value("${fx.providers.exchange-rates-api.api-key:demo}")
	private String apiKey;

	@Value("${fx.providers.exchange-rates-api.base-currency:EUR}")
	private String baseCurrency;

	public ExchangeRatesApiClient(WebClient.Builder webClientBuilder,
	                              @Value("${fx.providers.exchange-rates-api.base-url:https://api.exchangeratesapi.io/v1}")
	                              String baseUrl) {
		this.webClient = webClientBuilder.baseUrl(baseUrl).build();
	}

	@Override
	@CircuitBreaker(name = "exchange-rates-api", fallbackMethod = "fetchRateFallback")
	@Retry(name = "exchange-rates-api")
	public Optional<RateQuote> fetchRate(CurrencyPair pair) {
		Map<String, Object> response = webClient.get()
				.uri(uriBuilder -> uriBuilder
						.path("/latest")
						.queryParam("access_key", apiKey)
						.queryParam("base", pair.getBase().getCode())
						.queryParam("symbols", pair.getQuote().getCode())
						.build())
				.retrieve()
				.bodyToMono(Map.class)
				.block();

		return parseRate(response, pair);
	}

	@Override
	@CircuitBreaker(name = "exchange-rates-api", fallbackMethod = "fetchAllRatesFallback")
	@Retry(name = "exchange-rates-api")
	@SuppressWarnings("unchecked")
	public Map<CurrencyPair, RateQuote> fetchAllRates() {
		Map<String, Object> response = webClient.get()
				.uri(uriBuilder -> uriBuilder
						.path("/latest")
						.queryParam("access_key", apiKey)
						.queryParam("base", baseCurrency)
						.build())
				.retrieve()
				.bodyToMono(Map.class)
				.block();

		if (response == null || !response.containsKey("rates")) return Map.of();

		Map<String, Object> rates = (Map<String, Object>) response.get("rates");
		Currency base = Currency.fromCode(baseCurrency);
		Map<CurrencyPair, RateQuote> result = new HashMap<>();

		for (var entry : rates.entrySet()) {
			try {
				Currency quote = Currency.fromCode(entry.getKey());
				if (base == quote) continue;
				CurrencyPair pair = CurrencyPair.of(base, quote);
				BigDecimal mid = new BigDecimal(entry.getValue().toString());
				result.put(pair, new RateQuote(pair, mid, Instant.now(), getProviderName()));
			} catch (IllegalArgumentException ex) {
				log.debug("Skipping unsupported currency: {}", entry.getKey());
			}
		}

		return result;
	}

	@SuppressWarnings("unchecked")
	private Optional<RateQuote> parseRate(Map<String, Object> response, CurrencyPair pair) {
		if (response == null || !response.containsKey("rates")) return Optional.empty();
		Map<String, Object> rates = (Map<String, Object>) response.get("rates");
		Object rateValue = rates.get(pair.getQuote().getCode());
		if (rateValue == null) return Optional.empty();
		return Optional.of(new RateQuote(
				pair, new BigDecimal(rateValue.toString()), Instant.now(), getProviderName()
		));
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