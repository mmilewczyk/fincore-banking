package com.matcodem.fincore.notification.infrastructure.channel;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.matcodem.fincore.notification.domain.model.RecipientContact;
import com.matcodem.fincore.notification.domain.port.out.UserContactResolver;

import lombok.extern.slf4j.Slf4j;

/**
 * Stub UserContactResolver - used in local dev and tests.
 * <p>
 * PRODUCTION REPLACEMENT:
 * In production, replace with a real implementation that reads from
 * a local `notification_users` read-model table populated by:
 * - AccountCreatedEvent (provides ownerId + accountId mapping)
 * - UserRegisteredEvent (provides email, phone, FCM token)
 * <p>
 * This table is the notification service's own read-model - it does NOT call
 * account-service or identity-service synchronously on the hot path.
 * <p>
 * FCM token management:
 * Mobile app sends FCM token to an API endpoint on this service when:
 * - App starts
 * - FCM token refreshes (Google rotates tokens periodically)
 * Token is stored in notification_users.fcm_token.
 *
 * @Profile("!prod") ensures this stub never runs in production.
 */
@Slf4j
@Component
@Profile("!prod")
public class StubUserContactResolver implements UserContactResolver {

	@Override
	public RecipientContact resolveByUserId(String userId) {
		log.debug("StubUserContactResolver: resolveByUserId={}", userId);
		return new RecipientContact(
				userId,
				userId + "@example.com",
				"+48123456789",
				"stub-fcm-token-" + userId
		);
	}

	@Override
	public RecipientContact resolveByAccountId(String accountId) {
		log.debug("StubUserContactResolver: resolveByAccountId={}", accountId);
		String derivedUserId = "user-for-" + accountId;
		return new RecipientContact(
				derivedUserId,
				derivedUserId + "@example.com",
				"+48123456789",
				"stub-fcm-token-" + derivedUserId
		);
	}
}