package com.matcodem.fincore.account.domain.model;

public enum Currency {
	PLN("PLN", "Polish Złoty", 2),
	EUR("EUR", "Euro", 2),
	USD("USD", "US Dollar", 2),
	GBP("GBP", "British Pound", 2),
	CHF("CHF", "Swiss Franc", 2),
	JPY("JPY", "Japanese Yen", 0);

	private final String code;
	private final String displayName;
	private final int decimalPlaces;

	Currency(String code, String displayName, int decimalPlaces) {
		this.code = code;
		this.displayName = displayName;
		this.decimalPlaces = decimalPlaces;
	}

	public String getCode() { return code; }
	public String getDisplayName() { return displayName; }
	public int getDecimalPlaces() { return decimalPlaces; }

	public static Currency fromCode(String code) {
		for (Currency c : values()) {
			if (c.code.equalsIgnoreCase(code)) return c;
		}
		throw new IllegalArgumentException("Unknown currency code: " + code);
	}
}