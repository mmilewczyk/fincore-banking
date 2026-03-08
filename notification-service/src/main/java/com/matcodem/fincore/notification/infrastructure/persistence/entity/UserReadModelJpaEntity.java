package com.matcodem.fincore.notification.infrastructure.persistence.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Local read-model: account -> user contact mapping.
 * <p>
 * Populated by UserReadModelConsumer listening to AccountCreatedEvent.
 * Updated by UserReadModelConsumer on UserContactUpdatedEvent (FCM token refresh, phone change).
 * <p>
 * WHY A LOCAL READ-MODEL INSTEAD OF CALLING ACCOUNT-SERVICE DIRECTLY:
 * - No synchronous HTTP on the Kafka consumer hot path (no latency spike, no cascading failure)
 * - No circular dependency: notification-service -> account-service would create a cycle
 * because account-service emits events consumed by notification-service
 * - Resilience: notification-service works even if account-service is temporarily down
 * - This is the standard "choreography over orchestration" pattern for event-driven systems
 * <p>
 * CONSISTENCY MODEL:
 * Eventual consistency - if a user changes their phone number, notifications in-flight
 * may use the old number. This is acceptable: the old number is what the user consented
 * to at the time the notification was created (GDPR: contact snapshot on notification).
 * The read-model catches up within seconds of the AccountUpdated event being consumed.
 */
@Entity
@Table(name = "notification_user_read_model", indexes = {
		@Index(name = "idx_user_rm_user_id", columnList = "user_id", unique = true),
		@Index(name = "idx_user_rm_account_id", columnList = "account_id", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
public class UserReadModelJpaEntity {

	@Id
	@Column(name = "user_id", nullable = false)
	private String userId;

	@Column(name = "account_id", nullable = false)
	private String accountId;

	@Column(name = "email", nullable = false)
	private String email;

	@Column(name = "phone_number")
	private String phoneNumber;

	@Column(name = "fcm_token", columnDefinition = "TEXT")
	private String fcmToken;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;
}