package com.matcodem.fincore.fx.infrastructure.client;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.matcodem.fincore.fx.domain.model.Currency;
import com.matcodem.fincore.fx.domain.model.CurrencyPair;
import com.matcodem.fincore.fx.domain.port.out.RateProviderClient;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;

/**
 * Fallback rate provider #2 - European Central Bank daily reference rates.
 * <p>
 * ECB publishes rates daily at ~16:00 CET for ~30 currency pairs vs EUR.
 * Free, no API key required, highly reliable.
 * <p>
 * XML endpoint: https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml
 * <p>
 * NOTE: ECB only publishes EUR-based rates - pairs like USD/PLN are computed
 * as cross rates: USD/PLN = (1/EUR/USD) * (EUR/PLN)
 */
@Slf4j
@Component
public class EcbRateClient implements RateProviderClient {

	private static final String ECB_URL =
			"https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml";

	private static final Pattern RATE_PATTERN =
			Pattern.compile("currency='([A-Z]{3})'\\s+rate='([0-9.]+)'");

	private final WebClient webClient;

	public EcbRateClient(WebClient.Builder builder) {
		this.webClient = builder.build();
	}

	@Override
	@CircuitBreaker(name = "ecb-provider", fallbackMethod = "fetchRateFallback")
	public Optional<RateQuote> fetchRate(CurrencyPair pair) {
		// ECB only has EUR-based rates
		if (pair.getBase() != Currency.EUR && pair.getQuote() != Currency.EUR) {
			return Optional.empty(); // cross rate - not directly available
		}

		Map<CurrencyPair, RateQuote> allRates = fetchAllRates();
		return Optional.ofNullable(allRates.get(pair));
	}

	@Override
	@CircuitBreaker(name = "ecb-provider", fallbackMethod = "fetchAllRatesFallback")
	public Map<CurrencyPair, RateQuote> fetchAllRates() {
		String xml = webClient.get()
				.uri(ECB_URL)
				.retrieve()
				.bodyToMono(String.class)
				.block();

		return parseXml(xml);
	}

	private Map<CurrencyPair, RateQuote> parseXml(String xml) {
		if (xml == null || xml.isBlank()) return Map.of();

		Map<CurrencyPair, RateQuote> result = new HashMap<>();
		Matcher matcher = RATE_PATTERN.matcher(xml);
		Instant now = Instant.now();

		while (matcher.find()) {
			try {
				Currency quote = Currency.fromCode(matcher.group(1));
				BigDecimal rate = new BigDecimal(matcher.group(2));
				CurrencyPair pair = CurrencyPair.of(Currency.EUR, quote);
				result.put(pair, new RateQuote(pair, rate, now, getProviderName()));
			} catch (IllegalArgumentException ex) {
				log.debug("Skipping unsupported ECB currency: {}", matcher.group(1));
			}
		}

		log.info("ECB parsed {} rates", result.size());
		return result;
	}

	private Optional<RateQuote> fetchRateFallback(CurrencyPair pair, Exception ex) {
		log.warn("ECB circuit open for {}: {}", pair, ex.getMessage());
		return Optional.empty();
	}

	private Map<CurrencyPair, RateQuote> fetchAllRatesFallback(Exception ex) {
		log.warn("ECB bulk fetch failed: {}", ex.getMessage());
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
