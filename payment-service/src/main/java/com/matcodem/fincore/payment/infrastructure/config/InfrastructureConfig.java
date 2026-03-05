package com.matcodem.fincore.payment.infrastructure.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableScheduling
public class InfrastructureConfig {

	@Value("${spring.data.redis.host:localhost}")
	private String redisHost;

	@Value("${spring.data.redis.port:6379}")
	private int redisPort;

	@Value("${spring.data.redis.password:fincore_redis_secret}")
	private String redisPassword;

	/**
	 * Redisson client — provides distributed locks with watchdog.
	 * Watchdog default: 30s TTL, renewed every 10s.
	 */
	@Bean(destroyMethod = "shutdown")
	public RedissonClient redissonClient() {
		Config config = new Config();
		config.useSingleServer()
				.setAddress("redis://%s:%d".formatted(redisHost, redisPort))
				.setPassword(redisPassword)
				.setConnectionMinimumIdleSize(2)
				.setConnectionPoolSize(10)
				.setLockWatchdogTimeout(30_000); // watchdog TTL: 30 seconds
		return Redisson.create(config);
	}

	@Bean
	public RestTemplate restTemplate() {
		return new RestTemplate();
	}
}