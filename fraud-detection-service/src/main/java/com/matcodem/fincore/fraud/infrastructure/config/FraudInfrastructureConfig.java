package com.matcodem.fincore.fraud.infrastructure.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableCaching
public class FraudInfrastructureConfig {

	@Bean
	public RestTemplate restTemplate() {
		return new RestTemplate();
	}
}