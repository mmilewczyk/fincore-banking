package com.matcodem.fincore.notification.adapter.in.messaging;

import java.time.Instant;
import java.util.Optional;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.matcodem.fincore.account.avro.AccountCreatedEvent;
import com.matcodem.fincore.notification.infrastructure.persistence.entity.UserReadModelJpaEntity;
import com.matcodem.fincore.notification.infrastructure.persistence.repository.UserReadModelJpaRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Maintains the local user contact read-model from AccountCreatedEvent.
 * <p>
 * ── DATA FLOW ────────────────────────────────────────────────────────────────
 * <p>
 * Client calls POST /api/v1/accounts with { currency, email, phoneNumber }.
 * AccountController extracts ownerId from JWT, builds OpenAccountCommand with all fields.
 * AccountApplicationService calls Account.open(ownerId, iban, currency, email, phone).
 * Account.open() records AccountCreatedEvent (domain) with email + phone.
 * KafkaDomainEventPublisher maps to Avro AccountCreatedEvent (schema v2) and publishes.
 * This consumer reads email + phone directly from the event - zero HTTP calls.
 * <p>
 * ── BACKWARD COMPATIBILITY ───────────────────────────────────────────────────
 * <p>
 * AccountCreatedEvent.avsc schema v2 adds email and phoneNumber as Avro union
 * ["null", "string"] with default null. This is a BACKWARD compatible change -
 * old consumers reading schema v1 events simply get null for both new fields.
 * Old producers (schema v1) can still be consumed by this service - the fields
 * will be null and the email placeholder will remain until a contact update arrives.
 * <p>
 * ── IDEMPOTENCY ──────────────────────────────────────────────────────────────
 * <p>
 * At-least-once delivery: if the same AccountCreatedEvent is re-delivered,
 * the upsert overwrites with the same values - no side effect.
 * FCM token (registered via POST /api/v1/devices/register) is preserved on
 * re-delivery - it comes from a different source and must not be overwritten.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserReadModelConsumer {

	static final String GROUP_ID = "notification-service-user-read-model";

	private final UserReadModelJpaRepository readModelRepository;

	@KafkaListener(
			topics = "fincore.accounts.account-created",
			groupId = GROUP_ID,
			containerFactory = "notificationKafkaListenerContainerFactory"
	)
	@Transactional
	public void onAccountCreated(ConsumerRecord<String, Object> record, Acknowledgment ack) {
		AccountCreatedEvent event = (AccountCreatedEvent) record.value();

		String userId = event.getOwnerId().toString();
		String accountId = event.getAccountId().toString();
		String email = event.getEmail() != null ? event.getEmail().toString() : null;
		String phone = event.getPhoneNumber() != null ? event.getPhoneNumber().toString().strip() : null;
		if (phone != null && phone.isEmpty()) phone = null;

		try {
			Optional<UserReadModelJpaEntity> existing =
					readModelRepository.findByAccountId(accountId);

			if (existing.isPresent()) {
				// Idempotent re-delivery - update contact fields, preserve FCM token
				UserReadModelJpaEntity entity = existing.get();
				if (email != null) entity.setEmail(email);
				if (phone != null) entity.setPhoneNumber(phone);
				entity.setUpdatedAt(Instant.now());
				readModelRepository.save(entity);
				log.debug("UserReadModel updated (re-delivery): userId={}", userId);
			} else {
				UserReadModelJpaEntity entity = new UserReadModelJpaEntity();
				entity.setUserId(userId);
				entity.setAccountId(accountId);
				entity.setEmail(email != null ? email : userId + "@pending.fincore");
				entity.setPhoneNumber(phone);
				entity.setFcmToken(null); // set separately via POST /api/v1/devices/register
				entity.setCreatedAt(Instant.now());
				entity.setUpdatedAt(Instant.now());
				readModelRepository.save(entity);
				log.info("UserReadModel created: userId={}, accountId={}, hasEmail={}, hasPhone={}",
						userId, accountId, email != null, phone != null);
			}

			ack.acknowledge();

		} catch (Exception ex) {
			log.error("Failed to process AccountCreatedEvent: accountId={}: {}",
					accountId, ex.getMessage(), ex);
			throw new RuntimeException("UserReadModel update failed for accountId=" + accountId, ex);
		}
	}
}