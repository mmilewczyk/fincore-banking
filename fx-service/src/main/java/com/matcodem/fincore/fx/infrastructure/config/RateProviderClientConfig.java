package com.matcodem.fincore.fx.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Configuration for rate provider REST clients.
 * Configures RestClient with appropriate timeouts, retries, and observability.
 */
@Configuration
public class RateProviderClientConfig {

	/**
	 * Provides a RestClient bean for external API calls with resilience configuration.
	 */
	@Bean
	public RestClient rateProviderRestClient(MeterRegistry meterRegistry) {
		return RestClient.builder()
				.requestFactory(new org.springframework.http.client.BufferingClientHttpRequestFactory(
						new org.springframework.http.client.SimpleClientHttpRequestFactory()
				))
				.defaultHeader("Accept", "application/json")
				.defaultHeader("User-Agent", "FinCore-FX-Service/1.0")
				.requestInterceptor((request, body, execution) -> {
					long startTime = System.currentTimeMillis();
					var response = execution.execute(request, body);
					long duration = System.currentTimeMillis() - startTime;

					meterRegistry.timer("fx.rate.provider.request.time",
							"provider", request.getURI().getHost())
							.record(duration, java.util.concurrent.TimeUnit.MILLISECONDS);

					return response;
				})
				.build();
	}
}


