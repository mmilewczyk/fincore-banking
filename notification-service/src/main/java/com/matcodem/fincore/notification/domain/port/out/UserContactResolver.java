package com.matcodem.fincore.notification.domain.port.out;

import com.matcodem.fincore.notification.domain.model.RecipientContact;

/**
 * Resolves user contact details (email, phone, FCM token) for notification dispatch.
 *
 * Two resolution paths:
 *
 * resolveByUserId(userId):
 *   Used for payment events - JWT contains initiatedBy (userId) directly.
 *
 * resolveByAccountId(accountId):
 *   Used for account events - events carry accountId, not ownerId.
 *   Implementation must maintain a local account->user read-model
 *   (populated by consuming AccountCreatedEvents) to avoid synchronous
 *   HTTP calls to the account service on the Kafka hot path.
 *
 * In production: both resolution paths read from a local notifications_users
 * table populated by AccountCreatedEvent and UserRegisteredEvent consumers.
 *
 * For portfolio demo: stub implementation returns synthetic contacts.
 */
public interface UserContactResolver {
	RecipientContact resolveByUserId(String userId);
	RecipientContact resolveByAccountId(String accountId);

	// Backward-compat default for callers using the original method name
	default RecipientContact resolve(String userId) { return resolveByUserId(userId); }
}
