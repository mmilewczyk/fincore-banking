package com.matcodem.fincore.payment.adapter.out.client;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;

/**
 * Adapter to FX Service.
 * <p>
 * K8s DNS: http://fx-service.fincore.svc.cluster.local:8084
 * <p>
 * Payment flow involving FX:
 * 1. Payment Service receives FX_CONVERSION payment request
 * 2. Calls FxServiceClient.convert() synchronously
 * → FX Service locks rate, records conversion, returns converted amount
 * 3. Payment Service uses converted amount for account debit/credit
 * <p>
 * Quote flow (no side effects):
 * - Called by payment before committing, to show customer the rate
 * - Returns rate + fee estimate, nothing is persisted
 */
@Slf4j
@Component
public class FxServiceWebClient {

	private final WebClient webClient;

	public FxServiceWebClient(
			WebClient.Builder builder,
			@Value("${services.fx.base-url:http://fx-service.fincore.svc.cluster.local:8084}")
			String baseUrl) {
		this.webClient = builder.baseUrl(baseUrl).build();
	}

	/**
	 * Execute FX conversion — rate is locked, conversion persisted in FX Service.
	 * Returns the converted amount to use for account operations.
	 */
	@CircuitBreaker(name = "fx-service", fallbackMethod = "convertFallback")
	public FxConversionResult convert(String paymentId, String accountId, String requestedBy,
	                                  String pair, BigDecimal sourceAmount, String direction) {
		log.info("FX conversion requested — payment: {}, pair: {}, amount: {} direction: {}",
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

		if (response == null) throw new FxServiceUnavailableException("Empty FX response for payment: " + paymentId);

		BigDecimal convertedAmount = new BigDecimal(response.get("convertedAmount").toString());
		BigDecimal appliedRate = new BigDecimal(response.get("appliedRate").toString());
		BigDecimal fee = new BigDecimal(response.get("fee").toString());
		String conversionId = response.get("id").toString();

		log.info("FX conversion complete — payment: {}, {} → {} (rate: {}, fee: {})",
				paymentId, sourceAmount, convertedAmount, appliedRate, fee);

		return new FxConversionResult(conversionId, convertedAmount, appliedRate, fee);
	}

	/**
	 * Get a rate quote without executing anything — for payment preview.
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
	                                           Exception ex) {
		log.error("FX Service unavailable — conversion failed for payment {}: {}", paymentId, ex.getMessage());
		throw new FxServiceUnavailableException("FX Service unavailable for payment: " + paymentId);
	}

	private Optional<FxQuote> quoteFallback(String pair, BigDecimal amount,
	                                        String direction, Exception ex) {
		log.warn("FX Service unavailable for quote — pair: {}: {}", pair, ex.getMessage());
		return Optional.empty();
	}

	// ─── Records ──────────────────────────────────────────────────

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