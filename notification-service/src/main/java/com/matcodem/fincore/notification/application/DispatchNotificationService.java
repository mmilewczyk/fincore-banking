package com.matcodem.fincore.notification.application;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.matcodem.fincore.notification.domain.port.in.DispatchNotificationUseCase;
import com.matcodem.fincore.notification.domain.port.out.ChannelSendException;
import com.matcodem.fincore.notification.domain.port.out.ChannelSender;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * Dispatches a single notification via the correct channel sender.
 * <p>
 * Strategy Pattern:
 * All ChannelSender implementations are injected as a List by Spring.
 * They're indexed into a Map<NotificationChannel, ChannelSender> at construction.
 * dispatch() looks up the right sender by channel - no if/else or instanceof.
 * <p>
 * This means adding a new channel (e.g. WhatsApp) requires:
 * 1. New ChannelSender @Component implementing the interface
 * 2. New NotificationChannel enum value
 * 3. Route it in NotificationChannelRouter
 * 4. Zero changes to DispatchNotificationService
 * <p>
 * Transaction:
 *
 * @Transactional here ensures that the status update (markSent/markFailed) and
 * the save() are atomic. If the save fails after a successful send, the notification
 * stays PENDING - outbox poller will retry. The provider may receive a duplicate send,
 * but that's acceptable (idempotent from user's perspective - "payment confirmed" twice
 * is better than never). For SMS where duplicates matter, add provider-side deduplication
 * using Twilio's StatusCallback or idempotency key header.
 * <p>
 * NOT @Transactional at the send() call:
 * We don't want to hold the DB transaction open during the HTTP call to FCM/Twilio.
 * Status is updated after the call returns.
 */
@Slf4j
@Service
public class DispatchNotificationService implements DispatchNotificationUseCase {

	private final Map<com.matcodem.fincore.notification.domain.model.NotificationChannel, ChannelSender> senders;
	private final com.matcodem.fincore.notification.domain.port.out.NotificationRepository notificationRepository;
	private final MeterRegistry meterRegistry;

	public DispatchNotificationService(List<ChannelSender> senderList,
	                                   com.matcodem.fincore.notification.domain.port.out.NotificationRepository notificationRepository,
	                                   MeterRegistry meterRegistry) {
		this.senders = senderList.stream()
				.collect(Collectors.toMap(ChannelSender::channel, Function.identity()));
		this.notificationRepository = notificationRepository;
		this.meterRegistry = meterRegistry;

		log.info("DispatchNotificationService initialized with channels: {}",
				this.senders.keySet());
	}

	@Override
	@Transactional
	public void dispatch(com.matcodem.fincore.notification.domain.model.Notification notification) {
		ChannelSender sender = senders.get(notification.getChannel());
		if (sender == null) {
			// No sender registered for this channel - programming error, fail fast
			notification.markFailed("No sender registered for channel: " + notification.getChannel());
			notificationRepository.save(notification);
			return;
		}

		try {
			sender.send(notification);
			notification.markSent();
			notificationRepository.save(notification);

			meterRegistry.counter("notification.sent",
					"channel", notification.getChannel().name(),
					"type", notification.getType().name()).increment();

			log.info("Notification sent: id={}, channel={}, type={}, userId={}",
					notification.getId(), notification.getChannel(),
					notification.getType(), notification.getRecipientUserId());

		} catch (ChannelSendException ex) {
			notification.markFailed(ex.getMessage());
			notificationRepository.save(notification);

			if (notification.isDeadLetter()) {
				log.error("DEAD LETTER notification: id={}, channel={}, type={}, userId={} - reason: {}",
						notification.getId(), notification.getChannel(),
						notification.getType(), notification.getRecipientUserId(), ex.getMessage());
				meterRegistry.counter("notification.dead_letter",
						"channel", notification.getChannel().name(),
						"type", notification.getType().name()).increment();
			} else {
				log.warn("Notification send failed (retry {}): id={}, channel={}, reason={}",
						notification.getRetryCount(), notification.getId(),
						notification.getChannel(), ex.getMessage());
				meterRegistry.counter("notification.failed",
						"channel", notification.getChannel().name(),
						"type", notification.getType().name()).increment();
			}
		}
	}
}
