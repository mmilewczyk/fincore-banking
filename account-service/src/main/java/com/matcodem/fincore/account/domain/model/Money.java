package com.matcodem.fincore.account.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Value Object representing monetary amount with currency.
 * Immutable by design - all operations return new instances.
 */
public final class Money {

	private final BigDecimal amount;
	private final Currency currency;

	private Money(BigDecimal amount, Currency currency) {
		if (amount == null) throw new IllegalArgumentException("Amount cannot be null");
		if (currency == null) throw new IllegalArgumentException("Currency cannot be null");
		if (amount.compareTo(BigDecimal.ZERO) < 0) {
			throw new IllegalArgumentException("Money amount cannot be negative: " + amount);
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

	public static Money zero(Currency currency) {
		return new Money(BigDecimal.ZERO, currency);
	}

	public Money add(Money other) {
		assertSameCurrency(other);
		return new Money(this.amount.add(other.amount), this.currency);
	}

	public Money subtract(Money other) {
		assertSameCurrency(other);
		BigDecimal result = this.amount.subtract(other.amount);
		if (result.compareTo(BigDecimal.ZERO) < 0) {
			throw new InsufficientFundsException(
					"Cannot subtract %s from %s".formatted(other, this)
			);
		}
		return new Money(result, this.currency);
	}

	public boolean isGreaterThan(Money other) {
		assertSameCurrency(other);
		return this.amount.compareTo(other.amount) > 0;
	}

	public boolean isGreaterThanOrEqual(Money other) {
		assertSameCurrency(other);
		return this.amount.compareTo(other.amount) >= 0;
	}

	public boolean isZero() {
		return this.amount.compareTo(BigDecimal.ZERO) == 0;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public Currency getCurrency() {
		return currency;
	}

	private void assertSameCurrency(Money other) {
		if (!this.currency.equals(other.currency)) {
			throw new CurrencyMismatchException(
					"Cannot operate on different currencies: %s vs %s".formatted(this.currency, other.currency)
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