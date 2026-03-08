package com.matcodem.fincore.notification.adapter.in.messaging;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.matcodem.fincore.account.avro.AccountCreditedEvent;
import com.matcodem.fincore.account.avro.AccountDebitedEvent;
import com.matcodem.fincore.account.avro.AccountFrozenEvent;
import com.matcodem.fincore.notification.domain.model.NotificationType;
import com.matcodem.fincore.notification.domain.model.RecipientContact;
import com.matcodem.fincore.notification.domain.port.in.SendNotificationUseCase;
import com.matcodem.fincore.notification.domain.port.out.UserContactResolver;
import com.matcodem.fincore.notification.domain.service.NotificationPayloadFactory;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Listens to account domain events (Avro) and triggers notification creation.
 * <p>
 * Topics consumed:
 * fincore.accounts.account-debited  -> ACCOUNT_DEBITED  (Push only - see router)
 * fincore.accounts.account-credited -> ACCOUNT_CREDITED (Push only)
 * fincore.accounts.account-frozen   -> ACCOUNT_FROZEN   (Email + Push + SMS)
 * <p>
 * Note on ownerId:
 * AccountDebitedEvent/AccountCreditedEvent do not have ownerId directly.
 * We resolve ownerId from accountId via UserContactResolver which must implement
 * the account->user lookup. In production this comes from a local read-model
 * populated by AccountCreatedEvent (which does have ownerId).
 * For simplicity in this implementation, UserContactResolver handles the mapping.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountEventKafkaConsumer {

	private final SendNotificationUseCase sendNotificationUseCase;
	private final UserContactResolver persistentUserContactResolver;
	private final NotificationPayloadFactory payloadFactory;
	private final MeterRegistry meterRegistry;

	@KafkaListener(
			topics = "fincore.accounts.account-debited",
			groupId = "notification-service-accounts",
			containerFactory = "notificationKafkaListenerContainerFactory",
			concurrency = "3"
	)
	public void onAccountDebited(ConsumerRecord<String, Object> record, Acknowledgment ack) {
		AccountDebitedEvent event = (AccountDebitedEvent) record.value();
		log.debug("AccountDebited received: accountId={}", event.getAccountId());

		try {
			// Resolve user from account - UserContactResolver handles account->user lookup
			RecipientContact contact = persistentUserContactResolver.resolveByAccountId(event.getAccountId());
			var payload = payloadFactory.forAccountDebited(
					event.getAccountId(),
					event.getAmount(),
					event.getCurrency().name(),
					event.getBalanceAfter()
			);
			sendNotificationUseCase.createNotifications(
					event.getEventId(), contact, NotificationType.ACCOUNT_DEBITED, payload);

			meterRegistry.counter("notification.consumer.account_debited").increment();
			ack.acknowledge();

		} catch (Exception ex) {
			log.error("Failed to create notifications for AccountDebited {}: {}",
					event.getAccountId(), ex.getMessage(), ex);
			throw new RuntimeException("Notification creation failed", ex);
		}
	}

	@KafkaListener(
			topics = "fincore.accounts.account-credited",
			groupId = "notification-service-accounts",
			containerFactory = "notificationKafkaListenerContainerFactory",
			concurrency = "3"
	)
	public void onAccountCredited(ConsumerRecord<String, Object> record, Acknowledgment ack) {
		AccountCreditedEvent event = (AccountCreditedEvent) record.value();

		try {
			RecipientContact contact = persistentUserContactResolver.resolveByAccountId(event.getAccountId());
			var payload = payloadFactory.forAccountCredited(
					event.getAccountId(),
					event.getAmount(),
					event.getCurrency().name(),
					event.getBalanceAfter()
			);
			sendNotificationUseCase.createNotifications(
					event.getEventId(), contact, NotificationType.ACCOUNT_CREDITED, payload);

			meterRegistry.counter("notification.consumer.account_credited").increment();
			ack.acknowledge();

		} catch (Exception ex) {
			log.error("Failed to create notifications for AccountCredited {}: {}",
					event.getAccountId(), ex.getMessage(), ex);
			throw new RuntimeException("Notification creation failed", ex);
		}
	}

	@KafkaListener(
			topics = "fincore.accounts.account-frozen",
			groupId = "notification-service-accounts",
			containerFactory = "notificationKafkaListenerContainerFactory",
			concurrency = "3"
	)
	public void onAccountFrozen(ConsumerRecord<String, Object> record, Acknowledgment ack) {
		AccountFrozenEvent event = (AccountFrozenEvent) record.value();
		log.warn("AccountFrozen received: accountId={}, reason={}", event.getAccountId(), event.getReason());

		try {
			RecipientContact contact = persistentUserContactResolver.resolveByAccountId(event.getAccountId());
			var payload = payloadFactory.forAccountFrozen(event.getAccountId(), event.getReason());
			sendNotificationUseCase.createNotifications(
					event.getEventId(), contact, NotificationType.ACCOUNT_FROZEN, payload);

			meterRegistry.counter("notification.consumer.account_frozen").increment();
			ack.acknowledge();

		} catch (Exception ex) {
			log.error("Failed to create notifications for AccountFrozen {}: {}",
					event.getAccountId(), ex.getMessage(), ex);
			throw new RuntimeException("Notification creation failed", ex);
		}
	}
}
