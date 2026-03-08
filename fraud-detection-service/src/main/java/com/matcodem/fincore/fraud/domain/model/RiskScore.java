package com.matcodem.fincore.fraud.domain.model;

import java.util.Objects;

/**
 * Value Object - Risk Score from 0 (no risk) to 100 (certain fraud).
 * <p>
 * Thresholds:
 * 0–29  -> LOW    (approve)
 * 30–59 -> MEDIUM (review)
 * 60–84 -> HIGH   (block + manual review)
 * 85+   -> CRITICAL (block + freeze account)
 */
public final class RiskScore {

	public static final int MIN = 0;
	public static final int MAX = 100;

	public static final int LOW_THRESHOLD = 30;
	public static final int MEDIUM_THRESHOLD = 60;
	public static final int HIGH_THRESHOLD = 85;

	private final int value;

	private RiskScore(int value) {
		if (value < MIN || value > MAX) {
			throw new IllegalArgumentException(
					"RiskScore must be between %d and %d, got: %d".formatted(MIN, MAX, value)
			);
		}
		this.value = value;
	}

	public static RiskScore of(int value) {
		return new RiskScore(value);
	}

	public static RiskScore zero() {
		return new RiskScore(0);
	}

	public static RiskScore max() {
		return new RiskScore(MAX);
	}

	/**
	 * Combines two scores - takes the higher value, never exceeds MAX.
	 * Used when aggregating multiple rule results.
	 */
	public RiskScore combine(RiskScore other) {
		return new RiskScore(Math.min(MAX, Math.max(this.value, other.value)));
	}

	/**
	 * Adds penalty points - saturates at MAX.
	 */
	public RiskScore add(int penalty) {
		return new RiskScore(Math.min(MAX, this.value + penalty));
	}

	public RiskLevel getLevel() {
		if (value < LOW_THRESHOLD) return RiskLevel.LOW;
		if (value < MEDIUM_THRESHOLD) return RiskLevel.MEDIUM;
		if (value < HIGH_THRESHOLD) return RiskLevel.HIGH;
		return RiskLevel.CRITICAL;
	}

	public boolean isAboveThreshold(int threshold) {
		return this.value >= threshold;
	}

	public boolean requiresBlock() {
		return value >= MEDIUM_THRESHOLD;
	}

	public boolean requiresAccountFreeze() {
		return value >= HIGH_THRESHOLD;
	}

	public int getValue() {
		return value;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof RiskScore r)) return false;
		return value == r.value;
	}

	@Override
	public int hashCode() {
		return Objects.hash(value);
	}

	@Override
	public String toString() {
		return "RiskScore(%d/%s)".formatted(value, getLevel());
	}
}