package com.matcodem.fincore.fx.domain.model;

/**
 * Source of the exchange rate.
 * <p>
 * Priority (fallback chain):
 * ECB -> EXCHANGE_RATES_API -> NBP -> MANUAL_OVERRIDE
 * <p>
 * MANUAL_OVERRIDE is used by treasury desk to set rates directly
 * when all providers are unavailable. Requires ROLE_TREASURY.
 */
public enum RateSource {
	ECB("European Central Bank - free, daily, high trust"),
	EXCHANGE_RATES_API("exchangeratesapi.io - paid, real-time, primary"),
	NBP("Narodowy Bank Polski - free, daily, PLN pairs"),
	MANUAL_OVERRIDE("Treasury desk override - requires ROLE_TREASURY"),
	CACHED("Served from Redis cache - original source tracked separately");

	private final String description;

	RateSource(String description) {
		this.description = description;
	}

	public String getDescription() {
		return description;
	}
}