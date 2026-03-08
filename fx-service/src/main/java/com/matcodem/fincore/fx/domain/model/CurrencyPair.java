package com.matcodem.fincore.fx.domain.model;

import java.util.Objects;

import lombok.Getter;

/**
 * Value Object - immutable currency pair (e.g. EUR/PLN, USD/PLN).
 * <p>
 * Canonical form: base/quote (ISO 4217).
 * Inverse pair is computed on demand - no separate storage needed.
 * <p>
 * Design note: CurrencyPair is symmetric in business logic but NOT
 * in rate fetching - EUR/PLN rate is fetched from provider, PLN/EUR
 * is its mathematical inverse. We always normalise to a provider-canonical
 * form (e.g. XXX/PLN) and invert when needed.
 */
@Getter
public final class CurrencyPair {

	private final Currency base;
	private final Currency quote;

	private CurrencyPair(Currency base, Currency quote) {
		Objects.requireNonNull(base, "Base currency required");
		Objects.requireNonNull(quote, "Quote currency required");
		if (base == quote) throw new IllegalArgumentException("Base and quote currencies must differ");
		this.base = base;
		this.quote = quote;
	}

	public static CurrencyPair of(Currency base, Currency quote) {
		return new CurrencyPair(base, quote);
	}

	public static CurrencyPair of(String base, String quote) {
		return new CurrencyPair(Currency.fromCode(base), Currency.fromCode(quote));
	}

	public static CurrencyPair fromSymbol(String symbol) {
		// Accepts "EURPLN", "EUR/PLN", "EUR_PLN"
		String cleaned = symbol.replaceAll("[/_-]", "").toUpperCase();
		if (cleaned.length() != 6) {
			throw new IllegalArgumentException("Invalid currency pair symbol: " + symbol);
		}
		return of(cleaned.substring(0, 3), cleaned.substring(3, 6));
	}

	public CurrencyPair inverse() {
		return new CurrencyPair(quote, base);
	}

	public boolean isInverse(CurrencyPair other) {
		return this.base == other.quote && this.quote == other.base;
	}

	/**
	 * Returns "EUR/PLN"
	 */
	public String getSymbol() {
		return base.getCode() + "/" + quote.getCode();
	}

	/**
	 * Returns "EURPLN" - used as Redis cache key
	 */
	public String getCacheKey() {
		return base.getCode() + quote.getCode();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof CurrencyPair cp)) return false;
		return base == cp.base && quote == cp.quote;
	}

	@Override
	public int hashCode() {
		return Objects.hash(base, quote);
	}

	@Override
	public String toString() {
		return getSymbol();
	}
}