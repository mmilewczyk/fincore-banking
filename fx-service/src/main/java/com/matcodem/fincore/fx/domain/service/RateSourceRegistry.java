package com.matcodem.fincore.fx.domain.service;

import java.util.Objects;

import com.matcodem.fincore.fx.domain.model.RateSource;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Registry for mapping provider names to RateSource enums.
 * Can be extended for custom provider configurations.
 */
@Slf4j
@UtilityClass
class RateSourceRegistry {

	static RateSource resolveSource(String providerName) {
		Objects.requireNonNull(providerName, "providerName cannot be null");

		return switch (providerName.toLowerCase()) {
			case "ecb" -> RateSource.ECB;
			case "exchangeratesapi" -> RateSource.EXCHANGE_RATES_API;
			case "nbp" -> RateSource.NBP;
			default -> {
				log.warn("Unknown provider '{}' - defaulting to EXCHANGE_RATES_API", providerName);
				yield RateSource.EXCHANGE_RATES_API;
			}
		};
	}
}
