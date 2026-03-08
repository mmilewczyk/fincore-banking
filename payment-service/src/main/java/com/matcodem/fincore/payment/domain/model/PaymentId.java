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

	/**
	 * Accepts UUID string or plain UUID — used from web layer and Kafka consumers.
	 */
	public static PaymentId of(String value) {
		try {
			return new PaymentId(UUID.fromString(value));
		} catch (IllegalArgumentException ex) {
			throw new IllegalArgumentException("Invalid PaymentId format: '" + value + "'", ex);
		}
	}

	public static PaymentId of(UUID value) {
		return new PaymentId(value);
	}

	@Override
	public String toString() {
		return value.toString();
	}
}