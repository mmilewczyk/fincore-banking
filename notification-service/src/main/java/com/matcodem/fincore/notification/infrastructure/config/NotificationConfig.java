package com.matcodem.fincore.notification.infrastructure.config;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.matcodem.fincore.notification.adapter.out.sms.SmsProperties;
import com.matcodem.fincore.notification.domain.service.NotificationChannelRouter;
import com.matcodem.fincore.notification.domain.service.NotificationPayloadFactory;
import com.twilio.Twilio;

import lombok.extern.slf4j.Slf4j;

/**
 * Infrastructure config for external provider SDKs and domain services.
 * <p>
 * Firebase initialization:
 * GOOGLE_APPLICATION_CREDENTIALS_BASE64 env var holds base64-encoded service account JSON.
 * This avoids mounting a file in K8s - use a Secret instead.
 * Alternative: use GOOGLE_APPLICATION_CREDENTIALS path (works in local dev with file).
 * <p>
 * Twilio initialization:
 * Called once at startup - Twilio.init() sets static credentials.
 * Thread-safe after initialization.
 *
 * @EnableScheduling: required for NotificationOutboxPoller @Scheduled to work.
 */
@Slf4j
@Configuration
@EnableScheduling
public class NotificationConfig {

	@Value("${notification.firebase.credentials-base64:}")
	private String firebaseCredentialsBase64;

	@Bean
	public FirebaseMessaging firebaseMessaging() throws IOException {
		if (firebaseCredentialsBase64.isBlank()) {
			log.warn("Firebase credentials not configured - push notifications will fail. " +
					"Set notification.firebase.credentials-base64 in application.yml or env.");
			// Return a no-op stub in dev if credentials not set
			return createStubFirebaseMessaging();
		}

		byte[] credentialsJson = Base64.getDecoder().decode(firebaseCredentialsBase64);
		GoogleCredentials credentials = GoogleCredentials.fromStream(
				new ByteArrayInputStream(credentialsJson));

		FirebaseOptions options = FirebaseOptions.builder()
				.setCredentials(credentials)
				.build();

		if (FirebaseApp.getApps().isEmpty()) {
			FirebaseApp.initializeApp(options);
		}

		return FirebaseMessaging.getInstance();
	}

	private FirebaseMessaging createStubFirebaseMessaging() {
		// In dev without credentials, initialize with mock credentials
		// PushChannelSender will throw ChannelSendException -> notification.markFailed()
		// This is acceptable in local dev - push notifications won't actually send.
		try {
			if (FirebaseApp.getApps().isEmpty()) {
				FirebaseOptions options = FirebaseOptions.builder()
						.setCredentials(GoogleCredentials.fromStream(
								new ByteArrayInputStream("{\"type\":\"service_account\"}".getBytes())))
						.setProjectId("fincore-dev")
						.build();
				FirebaseApp.initializeApp(options);
			}
			return FirebaseMessaging.getInstance();
		} catch (Exception ex) {
			log.warn("Could not initialize stub Firebase: {}", ex.getMessage());
			return null;
		}
	}

	@Bean
	public Void twilioInit(SmsProperties smsProperties) {
		if (smsProperties.getAccountSid() != null && !smsProperties.getAccountSid().isBlank()) {
			Twilio.init(smsProperties.getAccountSid(), smsProperties.getAuthToken());
			log.info("Twilio initialized with account SID: {}...",
					smsProperties.getAccountSid().substring(0, 6));
		} else {
			log.warn("Twilio credentials not configured - SMS will fail. " +
					"Set notification.sms.account-sid and notification.sms.auth-token.");
		}
		return null;
	}

	@Bean
	public NotificationChannelRouter notificationChannelRouter() {
		return new NotificationChannelRouter();
	}

	@Bean
	public NotificationPayloadFactory notificationPayloadFactory() {
		return new NotificationPayloadFactory();
	}
}
