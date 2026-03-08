package com.matcodem.fincore.notification.domain.port.out;

import com.matcodem.fincore.notification.domain.model.RecipientContact;

public interface UserContactResolver {
	RecipientContact resolveByUserId(String userId);

	RecipientContact resolveByAccountId(String accountId);

	default RecipientContact resolve(String userId) {
		return resolveByUserId(userId);
	}
}
