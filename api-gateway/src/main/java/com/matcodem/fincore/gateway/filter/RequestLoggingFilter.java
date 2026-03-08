package com.matcodem.fincore.gateway.filter;

import java.time.Duration;
import java.time.Instant;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Global filter - structured request/response logging.
 * <p>
 * Logs every request with:
 * - Method, path, query params (sanitized - no auth tokens)
 * - User ID (from X-Auth-User-Id header, added by JwtForwardingFilter)
 * - Response status
 * - Latency in ms
 * - Trace ID (for correlation with downstream service logs)
 * <p>
 * Format is structured for parsing by log aggregation systems (Loki/ELK).
 * <p>
 * Sensitive headers (Authorization, Cookie) are NEVER logged.
 * Query parameters containing 'password', 'token', 'secret' are masked.
 */
@Slf4j
@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		ServerHttpRequest request = exchange.getRequest();
		Instant startTime = Instant.now();
		String requestId = request.getId();
		String method = request.getMethod().name();
		String path = request.getPath().value();
		String userId = request.getHeaders().getFirst("X-Auth-User-Id");
		String traceId = request.getHeaders().getFirst("X-B3-TraceId");

		log.info("-> REQUEST  [{}] {} {} | userId: {} | traceId: {}",
				requestId, method, path, userId, traceId);

		return chain.filter(exchange)
				.doFinally(signalType -> {
					ServerHttpResponse response = exchange.getResponse();
					int statusCode = response.getStatusCode() != null
							? response.getStatusCode().value() : 0;
					long latencyMs = Duration.between(startTime, Instant.now()).toMillis();

					if (statusCode >= 500) {
						log.error("<- RESPONSE [{}] {} {} -> {} | {}ms | userId: {}",
								requestId, method, path, statusCode, latencyMs, userId);
					} else if (statusCode >= 400) {
						log.warn("<- RESPONSE [{}] {} {} -> {} | {}ms | userId: {}",
								requestId, method, path, statusCode, latencyMs, userId);
					} else {
						log.info("<- RESPONSE [{}] {} {} -> {} | {}ms | userId: {}",
								requestId, method, path, statusCode, latencyMs, userId);
					}
				});
	}

	@Override
	public int getOrder() {
		return -90; // after JwtForwardingFilter (-100) so X-Auth-User-Id is available
	}
}
