package com.matcodem.fincore.notification.infrastructure.channel;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.matcodem.fincore.notification.domain.model.RecipientContact;
import com.matcodem.fincore.notification.domain.port.out.UserContactResolver;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@Profile("dev")
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