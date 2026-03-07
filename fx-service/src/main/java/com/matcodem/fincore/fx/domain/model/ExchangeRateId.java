package com.matcodem.fincore.fx.domain.model;

import java.util.Objects;
import java.util.UUID;

public record ExchangeRateId(UUID value) {
	public ExchangeRateId {
		Objects.requireNonNull(value);
	}

	public static ExchangeRateId generate() {
		return new ExchangeRateId(UUID.randomUUID());
	}

	public static ExchangeRateId of(UUID v) {
		return new ExchangeRateId(v);
	}

	public static ExchangeRateId of(String v) {
		return new ExchangeRateId(UUID.fromString(v));
	}

	@Override
	public String toString() {
		return value.toString();
	}
}
