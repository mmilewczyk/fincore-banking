package com.matcodem.fincore.notification.domain.service;

import java.util.ArrayList;
import java.util.List;

import com.matcodem.fincore.notification.domain.model.NotificationChannel;
import com.matcodem.fincore.notification.domain.model.NotificationType;
import com.matcodem.fincore.notification.domain.model.RecipientContact;

/**
 * Pure domain service - maps NotificationType to the set of channels to use.
 * <p>
 * Routing policy is a business rule, not infrastructure config.
 * It lives in the domain layer because "which channel for which event" is decided
 * by compliance + product requirements, not by technical constraints.
 * <p>
 * Routing logic:
 * PAYMENT_COMPLETED       -> Email + Push              (confirmation, non-urgent)
 * PAYMENT_FAILED          -> Email + Push + SMS        (action required)
 * PAYMENT_FRAUD_REJECTED  -> Email + Push + SMS        (security alert - all channels)
 * ACCOUNT_DEBITED         -> Push only                 (high frequency, email = noise)
 * ACCOUNT_CREDITED        -> Push only                 (same)
 * ACCOUNT_FROZEN          -> Email + Push + SMS        (critical account action)
 * <p>
 * Contact-aware: if user has no FCM token, skip Push. If no phone, skip SMS.
 * This prevents creating DEAD_LETTER notifications for missing contact details.
 */
public class NotificationChannelRouter {

	public List<NotificationChannel> routeChannels(NotificationType type, RecipientContact contact) {
		List<NotificationChannel> base = baseChannels(type);
		return filterByContactAvailability(base, contact);
	}

	private List<NotificationChannel> baseChannels(NotificationType type) {
		return switch (type) {
			case PAYMENT_COMPLETED -> List.of(NotificationChannel.EMAIL, NotificationChannel.PUSH);
			case PAYMENT_FAILED ->
					List.of(NotificationChannel.EMAIL, NotificationChannel.PUSH, NotificationChannel.SMS);
			case PAYMENT_FRAUD_REJECTED ->
					List.of(NotificationChannel.EMAIL, NotificationChannel.PUSH, NotificationChannel.SMS);
			case ACCOUNT_DEBITED -> List.of(NotificationChannel.PUSH);
			case ACCOUNT_CREDITED -> List.of(NotificationChannel.PUSH);
			case ACCOUNT_FROZEN ->
					List.of(NotificationChannel.EMAIL, NotificationChannel.PUSH, NotificationChannel.SMS);
		};
	}

	private List<NotificationChannel> filterByContactAvailability(
			List<NotificationChannel> channels, RecipientContact contact) {
		List<NotificationChannel> available = new ArrayList<>();
		for (NotificationChannel ch : channels) {
			switch (ch) {
				case EMAIL -> available.add(ch);  // always available - email required
				case PUSH -> {
					if (contact.canReceivePush()) available.add(ch);
				}
				case SMS -> {
					if (contact.canReceiveSms()) available.add(ch);
				}
			}
		}
		return available;
	}
}
