package com.matcodem.fincore.payment.infrastructure.config;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.reactive.function.client.WebClient;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import reactor.netty.http.client.HttpClient;

/**
 * Infrastructure beans: Redisson, WebClient.Builder.
 * <p>
 * WebClient timeouts (Netty-level):
 * connect-timeout: 3s  - fail fast if the target service is unreachable
 * read-timeout:    5s  - fail if response takes > 5s (account/FX service SLA)
 * write-timeout:   5s  - fail if request body cannot be sent in 5s
 * <p>
 * These are TCP-level timeouts, separate from Resilience4j TimeLimiter
 * (which is a circuit-breaker-level timeout). Both are needed:
 * - Netty timeouts: prevent thread starvation from hanging connections
 * - Resilience4j: trip circuit after repeated timeouts
 * <p>
 * Redisson watchdog:
 * 30s TTL, auto-renewed every 10s (= TTL/3).
 * If payment-service pod crashes while holding a lock, the lock expires in ≤30s
 * allowing other pods to proceed.
 */
@Configuration
@EnableScheduling
public class InfrastructureConfig {

	@Value("${spring.data.redis.host:localhost}")
	private String redisHost;

	@Value("${spring.data.redis.port:6379}")
	private int redisPort;

	@Value("${spring.data.redis.password:fincore_redis_secret}")
	private String redisPassword;

	@Bean(destroyMethod = "shutdown")
	public RedissonClient redissonClient() {
		Config config = new Config();
		// set watchdog timeout globally
		config.setLockWatchdogTimeout(30_000L);

		config.useSingleServer()
				.setAddress("redis://%s:%d".formatted(redisHost, redisPort))
				.setPassword(redisPassword)
				.setConnectionMinimumIdleSize(2)
				.setConnectionPoolSize(10);

		return Redisson.create(config);
	}

	/**
	 * Shared WebClient.Builder with Netty-level timeouts.
	 * Each service client (AccountServiceWebClient, FxServiceWebClient)
	 * calls builder.baseUrl(...).build() to get their own instance.
	 */
	@Bean
	public WebClient.Builder webClientBuilder() {
		HttpClient httpClient = HttpClient.create()
				.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3_000)
				.responseTimeout(Duration.ofSeconds(5))
				.doOnConnected(conn -> conn
						.addHandlerLast(new ReadTimeoutHandler(5, TimeUnit.SECONDS))
						.addHandlerLast(new WriteTimeoutHandler(5, TimeUnit.SECONDS))
				);

		return WebClient.builder()
				.clientConnector(new ReactorClientHttpConnector(httpClient));
	}
}