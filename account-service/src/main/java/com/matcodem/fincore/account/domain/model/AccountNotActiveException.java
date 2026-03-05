package com.matcodem.fincore.account.domain.model;

public class AccountNotActiveException extends RuntimeException {
	public AccountNotActiveException(String message) {
		super(message);
	}
}