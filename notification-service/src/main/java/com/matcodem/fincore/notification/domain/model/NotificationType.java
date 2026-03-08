package com.matcodem.fincore.notification.domain.model;

/**
 * Notification types - each maps to a specific template and channel routing policy.
 * <p>
 * Channel routing policy (defined in NotificationChannelRouter):
 * PAYMENT_COMPLETED       -> Email + Push
 * PAYMENT_FAILED          -> Email + Push + SMS   (SMS for urgency)
 * PAYMENT_FRAUD_REJECTED  -> Email + Push + SMS   (security alert - all channels)
 * ACCOUNT_DEBITED         -> Push only            (high frequency, email would be noise)
 * ACCOUNT_CREDITED        -> Push only
 * ACCOUNT_FROZEN          -> Email + Push + SMS   (critical - all channels)
 */
public enum NotificationType {
	PAYMENT_COMPLETED,
	PAYMENT_FAILED,
	PAYMENT_FRAUD_REJECTED,
	ACCOUNT_DEBITED,
	ACCOUNT_CREDITED,
	ACCOUNT_FROZEN
}
