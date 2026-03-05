package com.matcodem.fincore.payment.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Guarantees that the same payment request submitted multiple times
 * (e.g. due to network retry) is only processed once.
 * <p>
 * The client generates a UUID per payment attempt and sends it
 * as X-Idempotency-Key header. If we've seen this key before,
 * we return the cached response instead of re-processing.
 */
public record IdempotencyKey(String value) {

	public IdempotencyKey {
		Objects.requireNonNull(value, "IdempotencyKey cannot be null");
		if (value.isBlank()) throw new IllegalArgumentException("IdempotencyKey cannot be blank");
		if (value.length() > 64) throw new IllegalArgumentException("IdempotencyKey too long (max 64 chars)");
	}

	public static IdempotencyKey of(String value) {
		return new IdempotencyKey(value);
	}

	public static IdempotencyKey generate() {
		return new IdempotencyKey(UUID.randomUUID().toString());
	}

	@Override
	public String toString() {
		return value;
	}
}