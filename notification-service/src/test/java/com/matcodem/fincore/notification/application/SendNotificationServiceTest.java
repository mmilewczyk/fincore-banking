package com.matcodem.fincore.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.matcodem.fincore.notification.domain.model.Notification;
import com.matcodem.fincore.notification.domain.model.NotificationChannel;
import com.matcodem.fincore.notification.domain.model.NotificationPayload;
import com.matcodem.fincore.notification.domain.model.NotificationStatus;
import com.matcodem.fincore.notification.domain.model.NotificationType;
import com.matcodem.fincore.notification.domain.model.RecipientContact;
import com.matcodem.fincore.notification.domain.port.out.NotificationRepository;
import com.matcodem.fincore.notification.domain.service.NotificationChannelRouter;

@ExtendWith(MockitoExtension.class)
class SendNotificationServiceTest {

	@Mock
	NotificationRepository notificationRepository;

	private SendNotificationService service;

	private final RecipientContact fullContact = new RecipientContact(
			"user-1", "user@example.com", "+48123456789", "fcm-token");

	private final NotificationPayload payload = NotificationPayload.of(
			"Title", "Body", Map.of());

	@BeforeEach
	void setUp() {
		service = new SendNotificationService(notificationRepository, new NotificationChannelRouter());
	}

	@Test
	void createNotifications_createsOnePerChannel() {
		when(notificationRepository.existsByCorrelationEventIdAndChannel(any(), any()))
				.thenReturn(false);

		List<String> ids = service.createNotifications(
				"event-id-1", fullContact, NotificationType.PAYMENT_FAILED, payload);

		// PAYMENT_FAILED -> EMAIL + PUSH + SMS for full contact
		assertThat(ids).hasSize(3);

		ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);
		verify(notificationRepository).saveAll(captor.capture());

		List<Notification> saved = captor.getValue();
		assertThat(saved).hasSize(3);
		assertThat(saved.stream().map(Notification::getChannel).toList())
				.containsExactlyInAnyOrder(
						NotificationChannel.EMAIL,
						NotificationChannel.PUSH,
						NotificationChannel.SMS);
		saved.forEach(n -> assertThat(n.getStatus()).isEqualTo(NotificationStatus.PENDING));
	}

	@Test
	void createNotifications_skipsDuplicateChannels() {
		// EMAIL already created, PUSH and SMS are new
		when(notificationRepository.existsByCorrelationEventIdAndChannel("event-id-1", NotificationChannel.EMAIL))
				.thenReturn(true);
		when(notificationRepository.existsByCorrelationEventIdAndChannel("event-id-1", NotificationChannel.PUSH))
				.thenReturn(false);
		when(notificationRepository.existsByCorrelationEventIdAndChannel("event-id-1", NotificationChannel.SMS))
				.thenReturn(false);

		List<String> ids = service.createNotifications(
				"event-id-1", fullContact, NotificationType.PAYMENT_FAILED, payload);

		assertThat(ids).hasSize(2); // EMAIL skipped
	}

	@Test
	void createNotifications_noChannelsAvailable_savesNothing() {
		// ACCOUNT_DEBITED -> PUSH only, but no FCM token
		RecipientContact emailOnly = new RecipientContact("u1", "u@e.com", null, null);

		List<String> ids = service.createNotifications(
				"event-id-1", emailOnly, NotificationType.ACCOUNT_DEBITED, payload);

		assertThat(ids).isEmpty();
		verify(notificationRepository, never()).saveAll(any());
	}
}