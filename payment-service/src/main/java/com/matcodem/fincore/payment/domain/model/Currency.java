package com.matcodem.fincore.payment.domain.model;

public enum Currency {
	PLN("PLN"), EUR("EUR"), USD("USD"), GBP("GBP"), CHF("CHF"), JPY("JPY");

	private final String code;

	Currency(String code) {
		this.code = code;
	}

	public String getCode() {
		return code;
	}

	public static Currency fromCode(String code) {
		for (Currency c : values()) {
			if (c.code.equalsIgnoreCase(code)) return c;
		}
		throw new IllegalArgumentException("Unknown currency: " + code);
	}
}