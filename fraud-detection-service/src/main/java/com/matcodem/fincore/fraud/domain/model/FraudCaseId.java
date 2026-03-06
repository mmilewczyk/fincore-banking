package com.matcodem.fincore.fraud.domain.model;

import java.util.Objects;
import java.util.UUID;

public record FraudCaseId(UUID value) {

	public FraudCaseId {
		Objects.requireNonNull(value);
	}

	public static FraudCaseId generate() {
		return new FraudCaseId(UUID.randomUUID());
	}

	public static FraudCaseId of(UUID v) {
		return new FraudCaseId(v);
	}

	public static FraudCaseId of(String v) {
		return new FraudCaseId(UUID.fromString(v));
	}

	@Override
	public String toString() {
		return value.toString();
	}
}