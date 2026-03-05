package com.matcodem.fincore.account.domain.model;

public class CurrencyMismatchException extends RuntimeException {
	public CurrencyMismatchException(String message) {
		super(message);
	}
}