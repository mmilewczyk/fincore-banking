package com.matcodem.fincore.notification.infrastructure.channel;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.matcodem.fincore.notification.domain.model.RecipientContact;
import com.matcodem.fincore.notification.domain.port.out.UserContactResolver;
import com.matcodem.fincore.notification.infrastructure.persistence.entity.UserReadModelJpaEntity;
import com.matcodem.fincore.notification.infrastructure.persistence.repository.UserReadModelJpaRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@Profile("prod")
@RequiredArgsConstructor
public class PersistentUserContactResolver implements UserContactResolver {

	private final UserReadModelJpaRepository readModelRepository;

	@Override
	public RecipientContact resolveByUserId(String userId) {
		return readModelRepository.findById(userId)
				.map(this::toContact)
				.orElseThrow(() -> {
					log.warn("No read-model entry for userId={}. " +
							"UserReadModelConsumer may be lagging.", userId);
					return new UserContactNotFoundException(
							"No contact data for userId=" + userId);
				});
	}

	@Override
	public RecipientContact resolveByAccountId(String accountId) {
		return readModelRepository.findByAccountId(accountId)
				.map(this::toContact)
				.orElseThrow(() -> {
					log.warn("No read-model entry for accountId={}. " +
							"AccountCreatedEvent may not have been consumed yet.", accountId);
					return new UserContactNotFoundException(
							"No contact data for accountId=" + accountId);
				});
	}

	private RecipientContact toContact(UserReadModelJpaEntity entity) {
		return new RecipientContact(
				entity.getUserId(),
				entity.getEmail(),
				entity.getPhoneNumber(),   // null -> SMS channel filtered out by NotificationChannelRouter
				entity.getFcmToken()       // null -> Push channel filtered out by NotificationChannelRouter
		);
	}

	/**
	 * Unchecked exception thrown when contact data is not yet in the read-model.
	 * Caught by SendNotificationService - skips fanout with a warning log.
	 * Does NOT propagate to Kafka - the original event is ACK'd; a notification
	 * gap is preferable to consumer stall or endless retry.
	 */
	public static class UserContactNotFoundException extends RuntimeException {
		public UserContactNotFoundException(String message) {
			super(message);
		}
	}
}