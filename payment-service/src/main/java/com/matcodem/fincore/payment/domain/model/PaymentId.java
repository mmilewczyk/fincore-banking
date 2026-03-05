package com.matcodem.fincore.payment.domain.model;

import java.util.Objects;
import java.util.UUID;

public record PaymentId(UUID value) {

	public PaymentId {
		Objects.requireNonNull(value, "PaymentId cannot be null");
	}

	public static PaymentId generate() {
		return new PaymentId(UUID.randomUUID());
	}

	public static PaymentId of(String value) {
		return new PaymentId(UUID.fromString(value));
	}

	public static PaymentId of(UUID value) {
		return new PaymentId(value);
	}

	@Override
	public String toString() {
		return value.toString();
	}
}