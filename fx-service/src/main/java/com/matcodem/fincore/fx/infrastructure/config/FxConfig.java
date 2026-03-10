package com.matcodem.fincore.fx.infrastructure.config;

import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.reactive.function.client.WebClient;

import com.matcodem.fincore.fx.domain.port.out.ExchangeRateRepository;
import com.matcodem.fincore.fx.domain.port.out.FxEventPublisher;
import com.matcodem.fincore.fx.domain.port.out.RateProviderClient;
import com.matcodem.fincore.fx.domain.service.RateRefreshService;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@EnableScheduling
@EnableCaching
@RequiredArgsConstructor
public class FxConfig {

	@Value("${fx.spread.default-bps:50}")
	private int defaultSpreadBps;

	@Value("${fx.rate.validity-minutes:60}")
	private int rateValidityMinutes;

	@Bean
	public RateRefreshService rateRefreshService(
			List<RateProviderClient> providers,
			ExchangeRateRepository rateRepository,
			FxEventPublisher eventPublisher,
			MeterRegistry meterRegistry) {
		return new RateRefreshService(
				providers, rateRepository, eventPublisher,
				defaultSpreadBps, Duration.ofMinutes(rateValidityMinutes), meterRegistry
		);
	}

	@Bean
	public WebClient.Builder webClientBuilder() {
		return WebClient.builder();
	}

	/**
	 * Scheduled rate refresh - runs every 5 minutes.
	 * Fetches from highest-priority provider, falls back automatically.
	 */
	@Bean
	public RateRefreshScheduler rateRefreshScheduler(RateRefreshService service, MeterRegistry metrics) {
		return new RateRefreshScheduler(service, metrics);
	}

	@RequiredArgsConstructor
	public static class RateRefreshScheduler {
		private final RateRefreshService service;
		private final MeterRegistry metrics;

		@Scheduled(fixedDelayString = "${fx.rate.refresh-interval-ms:300000}")
		public void refreshAllRates() {
			log.info("Scheduled rate refresh starting...");
			long start = System.currentTimeMillis();
			int count = service.refreshAllRates();
			long elapsed = System.currentTimeMillis() - start;

			metrics.counter("fx.rate.refresh.count").increment(count);
			metrics.timer("fx.rate.refresh.duration").record(
					Duration.ofMillis(elapsed)
			);

			log.info("Rate refresh complete - {} pairs updated in {}ms", count, elapsed);
		}
	}
}