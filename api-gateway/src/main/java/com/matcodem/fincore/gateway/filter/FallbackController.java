package com.matcodem.fincore.gateway.filter;

import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import lombok.extern.slf4j.Slf4j;

/**
 * Fallback responses when circuit breakers open.
 * <p>
 * Returns structured JSON with:
 * - service name (which service is down)
 * - retryAfter hint (seconds)
 * - timestamp
 * - traceId (for support to correlate with logs)
 * <p>
 * HTTP 503 Service Unavailable — correct status for circuit-open scenarios.
 * Clients should treat this as temporary and retry with exponential backoff.
 */
@Slf4j
@RestController
@RequestMapping("/fallback")
public class FallbackController {

	@GetMapping("/account-service")
	public ResponseEntity<Map<String, Object>> accountServiceFallback(ServerWebExchange exchange) {
		return fallbackResponse("account-service", exchange);
	}

	@GetMapping("/payment-service")
	public ResponseEntity<Map<String, Object>> paymentServiceFallback(ServerWebExchange exchange) {
		return fallbackResponse("payment-service", exchange);
	}

	@GetMapping("/fx-service")
	public ResponseEntity<Map<String, Object>> fxServiceFallback(ServerWebExchange exchange) {
		return fallbackResponse("fx-service", exchange);
	}

	@GetMapping("/fraud-service")
	public ResponseEntity<Map<String, Object>> fraudServiceFallback(ServerWebExchange exchange) {
		return fallbackResponse("fraud-service", exchange);
	}

	private ResponseEntity<Map<String, Object>> fallbackResponse(
			String serviceName, ServerWebExchange exchange) {

		String traceId = exchange.getRequest().getHeaders().getFirst("X-B3-TraceId");
		log.warn("Circuit breaker open — fallback triggered for: {} | traceId: {}", serviceName, traceId);

		return ResponseEntity
				.status(HttpStatus.SERVICE_UNAVAILABLE)
				.body(Map.of(
						"error", "SERVICE_UNAVAILABLE",
						"service", serviceName,
						"message", "The %s is temporarily unavailable. Please retry shortly.".formatted(serviceName),
						"retryAfterSeconds", 30,
						"timestamp", Instant.now().toString(),
						"traceId", traceId != null ? traceId : "unavailable"
				));
	}
}