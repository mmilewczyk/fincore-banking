package com.matcodem.fincore.payment.adapter.out.client;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;

/**
 * Adapter to FX Service - synchronous HTTP client with circuit breaker.
 * <p>
 * FX_CONVERSION payment flow:
 * 1. Payment approved by fraud service -> processPayment() called
 * 2. convert() called synchronously - FX Service locks a rate and persists
 * a FxConversion record, returns the converted amount
 * 3. Payment Service uses converted amount for account debit/credit
 */
@Slf4j
@Component
public class FxServiceWebClient {

	private final WebClient webClient;

	public FxServiceWebClient(
			WebClient.Builder webClientBuilder,
			@Value("${services.fx.base-url:http://fx-service.fincore.svc.cluster.local:8084}")
			String baseUrl) {
		this.webClient = webClientBuilder.baseUrl(baseUrl).build();
	}

	/**
	 * Execute FX conversion - idempotent via paymentId.
	 * FX Service deduplicates on paymentId: calling twice with the same ID
	 * returns the same FxConversion record without creating a duplicate.
	 */
	@CircuitBreaker(name = "fx-service", fallbackMethod = "convertFallback")
	@Retry(name = "fx-service")
	public FxConversionResult convert(String paymentId, String accountId, String requestedBy,
	                                  String pair, BigDecimal sourceAmount, String direction) {
		log.info("FX convert - payment: {}, pair: {}, amount: {}, direction: {}",
				paymentId, pair, sourceAmount, direction);

		@SuppressWarnings("unchecked")
		Map<String, Object> response = webClient.post()
				.uri("/api/v1/fx/convert")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(Map.of(
						"paymentId", paymentId,
						"accountId", accountId,
						"pair", pair,
						"sourceAmount", sourceAmount,
						"direction", direction
				))
				.retrieve()
				.bodyToMono(Map.class)
				.block();

		if (response == null) {
			throw new FxServiceUnavailableException("Empty FX response for payment: " + paymentId);
		}

		return new FxConversionResult(
				response.get("id").toString(),
				new BigDecimal(response.get("convertedAmount").toString()),
				new BigDecimal(response.get("appliedRate").toString()),
				new BigDecimal(response.get("fee").toString())
		);
	}

	/**
	 * Get a rate quote without side effects - for pre-flight amount preview.
	 * Returns empty on circuit open - caller should degrade gracefully.
	 */
	@CircuitBreaker(name = "fx-service", fallbackMethod = "quoteFallback")
	public Optional<FxQuote> quote(String pair, BigDecimal amount, String direction) {
		@SuppressWarnings("unchecked")
		Map<String, Object> response = webClient.post()
				.uri("/api/v1/fx/quote")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(Map.of("pair", pair, "amount", amount, "direction", direction))
				.retrieve()
				.bodyToMono(Map.class)
				.block();

		if (response == null) return Optional.empty();

		return Optional.of(new FxQuote(
				new BigDecimal(response.get("convertedAmount").toString()),
				new BigDecimal(response.get("appliedRate").toString()),
				new BigDecimal(response.get("fee").toString())
		));
	}

	private FxConversionResult convertFallback(String paymentId, String accountId,
	                                           String requestedBy, String pair,
	                                           BigDecimal sourceAmount, String direction,
	                                           Throwable ex) {
		log.error("FX Service CB open - convert failed for payment {}: {}", paymentId, ex.getMessage());
		throw new FxServiceUnavailableException("FX Service unavailable for payment: " + paymentId);
	}

	private Optional<FxQuote> quoteFallback(String pair, BigDecimal amount,
	                                        String direction, Throwable ex) {
		log.warn("FX Service CB open - quote unavailable for pair {}: {}", pair, ex.getMessage());
		return Optional.empty();
	}

	public record FxConversionResult(
			String conversionId,
			BigDecimal convertedAmount,
			BigDecimal appliedRate,
			BigDecimal fee
	) {
	}

	public record FxQuote(
			BigDecimal convertedAmount,
			BigDecimal appliedRate,
			BigDecimal fee
	) {
	}

	public static class FxServiceUnavailableException extends RuntimeException {
		public FxServiceUnavailableException(String msg) {
			super(msg);
		}
	}
}