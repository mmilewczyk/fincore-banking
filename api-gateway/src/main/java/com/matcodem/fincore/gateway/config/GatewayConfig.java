package com.matcodem.fincore.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import reactor.core.publisher.Mono;

/**
 * Rate Limiter configuration - Redis-backed token bucket per user.
 * <p>
 * Strategy: per authenticated user (X-Auth-User-Id header).
 * Falls back to IP address for unauthenticated requests.
 * <p>
 * Default limits (configured in application.yml):
 * Global API:  100 req/s, burst 200
 * Payments:     20 req/s, burst 40   (stricter - financial operations)
 * <p>
 * When rate limit exceeded -> 429 Too Many Requests
 */
@Configuration
public class GatewayConfig {

	/**
	 * Rate limit key: authenticated user ID, fallback to remote IP.
	 * This prevents one user from exhausting the global rate limit.
	 */
	@Bean
	public KeyResolver userKeyResolver() {
		return exchange -> {
			String userId = exchange.getRequest().getHeaders().getFirst("X-Auth-User-Id");
			if (userId != null && !userId.isBlank()) {
				return Mono.just("user:" + userId);
			}
			// Fallback for unauthenticated (should be rejected by security filter, but just in case)
			String ip = exchange.getRequest().getRemoteAddress() != null
					? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
					: "unknown";
			return Mono.just("ip:" + ip);
		};
	}

	/**
	 * Explicit Redis rate limiter bean - allows programmatic configuration
	 * in addition to application.yml.
	 */
	@Bean
	public RedisRateLimiter redisRateLimiter() {
		return new RedisRateLimiter(100, 200, 1);
	}
}