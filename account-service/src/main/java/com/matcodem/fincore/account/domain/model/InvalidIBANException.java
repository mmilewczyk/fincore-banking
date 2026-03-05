package com.matcodem.fincore.account.domain.model;

public class InvalidIBANException extends RuntimeException {
	public InvalidIBANException(String message) {
		super(message);
	}
}
