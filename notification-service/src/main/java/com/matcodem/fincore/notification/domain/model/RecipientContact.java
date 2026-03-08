package com.matcodem.fincore.notification.domain.model;

import java.util.Objects;

/**
 * Contact details for a notification recipient.
 * Fetched from User Profile Service (or JWT claims for email) at event-consumption time.
 * <p>
 * Why store at notification creation (not at send time)?
 * - Notification record is created synchronously in the Kafka listener transaction.
 * - Actual sending is async (OutboxPoller). By send time, user may have changed contact.
 * - For GDPR: stored contact is the one the user consented to at notification time.
 * <p>
 * Fields are nullable: a user may have no phone number (SMS optional).
 * email is required - it's always present from JWT claims.
 */
public record RecipientContact(
		String userId,
		String email,         // required - from JWT / identity service
		String phoneNumber,   // nullable - SMS only if set
		String fcmToken       // nullable - push only if registered
) {
	public RecipientContact {
		Objects.requireNonNull(userId, "userId required");
		Objects.requireNonNull(email, "email required");
	}

	public boolean canReceiveSms() {
		return phoneNumber != null && !phoneNumber.isBlank();
	}

	public boolean canReceivePush() {
		return fcmToken != null && !fcmToken.isBlank();
	}
}