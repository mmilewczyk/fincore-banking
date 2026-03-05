package com.matcodem.fincore.payment.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public final class Money {

	private final BigDecimal amount;
	private final Currency currency;

	private Money(BigDecimal amount, Currency currency) {
		if (amount == null) throw new IllegalArgumentException("Amount cannot be null");
		if (currency == null) throw new IllegalArgumentException("Currency cannot be null");
		if (amount.compareTo(BigDecimal.ZERO) < 0) {
			throw new IllegalArgumentException("Money amount cannot be negative: " + amount);
		}
		if (amount.compareTo(BigDecimal.ZERO) == 0 && !currency.equals(Currency.PLN)) {
			// Allow zero only for specific cases
		}
		this.amount = amount.setScale(2, RoundingMode.HALF_UP);
		this.currency = currency;
	}

	public static Money of(BigDecimal amount, Currency currency) {
		return new Money(amount, currency);
	}

	public static Money of(String amount, Currency currency) {
		return new Money(new BigDecimal(amount), currency);
	}

	public boolean isPositive() {
		return amount.compareTo(BigDecimal.ZERO) > 0;
	}

	public boolean isGreaterThan(Money other) {
		assertSameCurrency(other);
		return this.amount.compareTo(other.amount) > 0;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public Currency getCurrency() {
		return currency;
	}

	private void assertSameCurrency(Money other) {
		if (!this.currency.equals(other.currency)) {
			throw new IllegalArgumentException(
					"Currency mismatch: %s vs %s".formatted(this.currency, other.currency)
			);
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof Money money)) return false;
		return Objects.equals(amount, money.amount) && currency == money.currency;
	}

	@Override
	public int hashCode() {
		return Objects.hash(amount, currency);
	}

	@Override
	public String toString() {
		return "%s %s".formatted(amount.toPlainString(), currency.getCode());
	}
}