package com.matcodem.fincore.notification.adapter.in.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Optional;

import com.matcodem.fincore.notification.infrastructure.persistence.entity.UserReadModelJpaEntity;
import com.matcodem.fincore.notification.infrastructure.persistence.repository.UserReadModelJpaRepository;

/**
 * REST endpoint for mobile app FCM token registration.
 *
 * FLOW:
 *   Mobile app calls POST /api/v1/devices/register on every app start and
 *   whenever Firebase rotates the FCM token (FirebaseMessaging.onTokenRefresh).
 *
 * WHY THIS IS NOTIFICATION-SERVICE'S RESPONSIBILITY:
 *   FCM tokens are notification infrastructure - they belong here, not in
 *   account-service or identity-service. This service owns the send path
 *   and therefore owns the token storage.
 *
 * SECURITY:
 *   JWT required - userId extracted from token sub claim.
 *   A user can only register tokens for their own sub.
 *   No admin override needed: token rotation is self-service.
 *
 * IDEMPOTENCY:
 *   Registering the same token twice is a no-op (same value written).
 *   Registering a new token replaces the old one - only latest token is kept.
 *   Google guarantees tokens are unique per app install, not per user,
 *   so one row per user is sufficient for single-device use cases.
 *   Multi-device would require a separate notification_devices table.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
public class DeviceRegistrationController {

	private final UserReadModelJpaRepository readModelRepository;

	public record RegisterDeviceRequest(@NotBlank String fcmToken) {}

	@PostMapping("/register")
	public ResponseEntity<Void> registerDevice(
			@Valid @RequestBody RegisterDeviceRequest request,
			@AuthenticationPrincipal Jwt jwt) {

		String userId = jwt.getSubject();

		Optional<UserReadModelJpaEntity> existing = readModelRepository.findById(userId);

		if (existing.isEmpty()) {
			// User's AccountCreatedEvent not yet consumed - create a minimal placeholder.
			// The consumer will fill in email/phone when the event arrives.
			// This handles the race: app starts and registers token before the Kafka consumer
			// has processed the AccountCreatedEvent for this user.
			log.warn("FCM token registered for unknown userId={} - read-model entry not yet created. " +
					"Creating placeholder; UserReadModelConsumer will fill contact details.", userId);
			UserReadModelJpaEntity placeholder = new UserReadModelJpaEntity();
			placeholder.setUserId(userId);
			placeholder.setAccountId("pending-" + userId);  // temporary, overwritten by consumer
			placeholder.setEmail(userId + "@pending.fincore"); // temporary
			placeholder.setFcmToken(request.fcmToken());
			placeholder.setCreatedAt(Instant.now());
			placeholder.setUpdatedAt(Instant.now());
			readModelRepository.save(placeholder);
		} else {
			UserReadModelJpaEntity entity = existing.get();
			String oldToken = entity.getFcmToken();
			entity.setFcmToken(request.fcmToken());
			entity.setUpdatedAt(Instant.now());
			readModelRepository.save(entity);
			if (!request.fcmToken().equals(oldToken)) {
				log.info("FCM token rotated for userId={}", userId);
			}
		}

		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/register")
	public ResponseEntity<Void> unregisterDevice(@AuthenticationPrincipal Jwt jwt) {
		String userId = jwt.getSubject();
		readModelRepository.findById(userId).ifPresent(entity -> {
			entity.setFcmToken(null);
			entity.setUpdatedAt(Instant.now());
			readModelRepository.save(entity);
			log.info("FCM token removed for userId={}", userId);
		});
		return ResponseEntity.noContent().build();
	}
}