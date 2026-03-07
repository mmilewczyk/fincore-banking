package com.matcodem.fincore.fx.domain.model;

import java.util.Objects;
import java.util.UUID;

public record FxConversionId(UUID value) {
	public FxConversionId {
		Objects.requireNonNull(value);
	}

	public static FxConversionId generate() {
		return new FxConversionId(UUID.randomUUID());
	}

	public static FxConversionId of(UUID v) {
		return new FxConversionId(v);
	}

	public static FxConversionId of(String v) {
		return new FxConversionId(UUID.fromString(v));
	}

	@Override
	public String toString() {
		return value.toString();
	}
}
