package com.matcodem.fincore.fx.domain.model;

public enum Currency {
	PLN("PLN", 2),
	EUR("EUR", 2),
	USD("USD", 2),
	GBP("GBP", 2),
	CHF("CHF", 2),
	JPY("JPY", 0),
	NOK("NOK", 2),
	SEK("SEK", 2),
	DKK("DKK", 2),
	CZK("CZK", 2),
	HUF("HUF", 0);

	private final String code;
	private final int decimalPlaces;

	Currency(String code, int decimalPlaces) {
		this.code = code;
		this.decimalPlaces = decimalPlaces;
	}

	public String getCode() {
		return code;
	}

	public int getDecimalPlaces() {
		return decimalPlaces;
	}

	public static Currency fromCode(String code) {
		for (Currency c : values()) {
			if (c.code.equalsIgnoreCase(code)) return c;
		}
		throw new IllegalArgumentException("Unsupported currency: " + code);
	}
}