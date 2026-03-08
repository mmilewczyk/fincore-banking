package com.matcodem.fincore.notification.application;

import com.matcodem.fincore.notification.domain.model.*;
import com.matcodem.fincore.notification.domain.port.in.SendNotificationUseCase;
import com.matcodem.fincore.notification.domain.port.out.NotificationRepository;
import com.matcodem.fincore.notification.domain.service.NotificationChannelRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Creates Notification aggregates for each applicable channel and persists them.
 *
 * TRANSACTION: @Transactional - all channel notifications for one event are saved
 * atomically. If saving Email succeeds but SMS fails, the transaction rolls back
 * and Kafka consumer does NOT acknowledge -> Kafka re-delivers -> clean retry.
 *
 * DEDUPLICATION: Before creating, checks if a notification for this
 * (correlationEventId, channel) already exists. This handles Kafka re-delivery
 * of the same event (at-least-once delivery guarantee).
 *
 * FAN-OUT DESIGN:
 * One domain event -> N Notification aggregates (one per channel).
 * Each notification is independently retried by the outbox poller.
 * This means Email and SMS have independent delivery state - one can succeed
 * while the other retries without coupling.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SendNotificationService implements SendNotificationUseCase {

	private final NotificationRepository notificationRepository;
	private final NotificationChannelRouter channelRouter;

	@Override
	@Transactional
	public List<String> createNotifications(
			String correlationEventId,
			RecipientContact contact,
			NotificationType type,
			NotificationPayload payload) {

		List<NotificationChannel> channels = channelRouter.routeChannels(type, contact);
		List<Notification> toSave = new ArrayList<>();
		List<String> createdIds = new ArrayList<>();

		for (NotificationChannel channel : channels) {
			// Idempotency guard - skip if already created for this event+channel pair
			if (notificationRepository.existsByCorrelationEventIdAndChannel(correlationEventId, channel)) {
				log.debug("Duplicate notification skipped: eventId={}, channel={}", correlationEventId, channel);
				continue;
			}

			Notification notification = Notification.create(
					correlationEventId,
					contact.userId(),
					type,
					channel,
					contact,
					payload
			);
			toSave.add(notification);
			createdIds.add(notification.getId().toString());
		}

		if (!toSave.isEmpty()) {
			notificationRepository.saveAll(toSave);
			log.info("Created {} PENDING notification(s) for eventId={}, type={}, channels={}",
					toSave.size(), correlationEventId, type,
					toSave.stream().map(n -> n.getChannel().name()).toList());
		}

		return createdIds;
	}
}