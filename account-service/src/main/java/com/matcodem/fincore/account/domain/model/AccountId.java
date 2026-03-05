package com.matcodem.fincore.account.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Value Object — strongly-typed Account identifier.
 * Prevents passing raw UUIDs where AccountId is expected.
 */
public record AccountId(UUID value) {

	public AccountId {
		Objects.requireNonNull(value, "AccountId value cannot be null");
	}

	public static AccountId generate() {
		return new AccountId(UUID.randomUUID());
	}

	public static AccountId of(String value) {
		return new AccountId(UUID.fromString(value));
	}

	public static AccountId of(UUID value) {
		return new AccountId(value);
	}

	@Override
	public String toString() {
		return value.toString();
	}
}