package com.matcodem.fincore.fx.infrastructure.client.exception;

import lombok.Getter;

/**
 * Thrown when rate provider client fails to fetch rates.
 */
@Getter
public class RateProviderException extends RuntimeException {

	private final String providerName;
	private final int httpStatus;

	public RateProviderException(String providerName, String message) {
		this(providerName, message, null, 0);
	}

	public RateProviderException(String providerName, String message, Throwable cause) {
		this(providerName, message, cause, 0);
	}

	public RateProviderException(String providerName, String message, int httpStatus) {
		this(providerName, message, null, httpStatus);
	}

	public RateProviderException(String providerName, String message, Throwable cause, int httpStatus) {
		super(String.format("[%s] %s (HTTP %d)", providerName, message, httpStatus), cause);
		this.providerName = providerName;
		this.httpStatus = httpStatus;
	}
}


