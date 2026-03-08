package com.matcodem.fincore.notification.domain.model;

public enum NotificationStatus {
	PENDING,    // created, not yet dispatched
	SENT,       // successfully delivered to provider (SMTP, FCM, Twilio)
	FAILED,     // provider returned error
	DEAD_LETTER // exceeded max retries - manual investigation required
}