package com.matcodem.fincore.notification.domain.port.out;

/**
 * Thrown by ChannelSender implementations on provider failure.
 * Caught by DispatchNotificationService - triggers notification.markFailed().
 */
public class ChannelSendException extends Exception {
	public ChannelSendException(String message) {
		super(message);
	}

	public ChannelSendException(String message, Throwable cause) {
		super(message, cause);
	}
}
