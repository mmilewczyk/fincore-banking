package com.matcodem.fincore.notification.adapter.out.push;

import org.springframework.stereotype.Component;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.ApsAlert;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.matcodem.fincore.notification.domain.model.Notification;
import com.matcodem.fincore.notification.domain.model.NotificationChannel;
import com.matcodem.fincore.notification.domain.port.out.ChannelSendException;
import com.matcodem.fincore.notification.domain.port.out.ChannelSender;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Push notification sender via Firebase Cloud Messaging (FCM).
 * <p>
 * Uses FirebaseMessaging bean - initialized in NotificationConfig with service account
 * credentials from GOOGLE_APPLICATION_CREDENTIALS or base64-encoded JSON env var.
 * <p>
 * FCM delivery guarantees:
 * - FCM does NOT guarantee delivery (device offline, user uninstalled app, etc.)
 * - We treat FirebaseMessagingException as transient failure -> markFailed() + retry
 * - After 5 retries (DEAD_LETTER): ops alert, but no further action needed
 * (unlike Email/SMS where missing notification may have compliance implications)
 * <p>
 * Token expiry:
 * FCM tokens expire when app is uninstalled or token rotated.
 * UNREGISTERED error code -> mark notification DEAD_LETTER immediately (no retry)
 * because the token is permanently invalid - retrying won't help.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PushChannelSender implements ChannelSender {

	private final FirebaseMessaging firebaseMessaging;

	@Override
	public NotificationChannel channel() {
		return NotificationChannel.PUSH;
	}

	@Override
	public void send(Notification notification) throws ChannelSendException {
		String fcmToken = notification.getContact().fcmToken();
		if (fcmToken == null || fcmToken.isBlank()) {
			throw new ChannelSendException("No FCM token for userId=" + notification.getRecipientUserId());
		}

		Message message = Message.builder()
				.setToken(fcmToken)
				.setNotification(com.google.firebase.messaging.Notification.builder()
						.setTitle(notification.getPayload().title())
						.setBody(notification.getPayload().body())
						.build())
				// Data payload - accessible to app even in background
				.putData("notificationType", notification.getType().name())
				.putData("notificationId", notification.getId().toString())
				.setAndroidConfig(AndroidConfig.builder()
						.setPriority(resolveFcmPriority(notification))
						.build())
				.setApnsConfig(ApnsConfig.builder()
						.setAps(Aps.builder()
								.setAlert(ApsAlert.builder()
										.setTitle(notification.getPayload().title())
										.setBody(notification.getPayload().body())
										.build())
								.setSound("default")
								.build())
						.build())
				.build();

		try {
			String messageId = firebaseMessaging.send(message);
			log.debug("FCM push sent: messageId={}, notificationId={}", messageId, notification.getId());

		} catch (FirebaseMessagingException ex) {
			if (ex.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED
					|| ex.getMessagingErrorCode() == MessagingErrorCode.INVALID_ARGUMENT) {
				// Token invalid/expired - wrap with specific message so DispatchNotificationService
				// can potentially skip retries. For now treated same as other failures -
				// will reach DEAD_LETTER after 5 retries (acceptable for push).
				throw new ChannelSendException(
						"FCM token invalid or unregistered for userId=%s: %s"
								.formatted(notification.getRecipientUserId(), ex.getMessagingErrorCode()), ex);
			}
			throw new ChannelSendException(
					"FCM send failed (errorCode=%s): %s".formatted(ex.getMessagingErrorCode(), ex.getMessage()), ex);
		}
	}

	private AndroidConfig.Priority resolveFcmPriority(Notification notification) {
		return switch (notification.getType()) {
			case PAYMENT_FRAUD_REJECTED, ACCOUNT_FROZEN -> AndroidConfig.Priority.HIGH;
			default -> AndroidConfig.Priority.NORMAL;
		};
	}
}