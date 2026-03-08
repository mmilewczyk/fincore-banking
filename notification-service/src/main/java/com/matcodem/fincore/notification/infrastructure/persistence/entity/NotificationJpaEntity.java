package com.matcodem.fincore.notification.infrastructure.persistence.entity;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "notifications", indexes = {
		@Index(name = "idx_notifications_status", columnList = "status"),
		@Index(name = "idx_notifications_recipient", columnList = "recipient_user_id"),
		@Index(name = "idx_notifications_correlation", columnList = "correlation_event_id, channel", unique = true),
		@Index(name = "idx_notifications_created_at", columnList = "created_at DESC")
})
@Getter
@Setter
@NoArgsConstructor
public class NotificationJpaEntity {

	@Id
	private UUID id;

	@Column(name = "correlation_event_id", nullable = false, length = 36)
	private String correlationEventId;

	@Column(name = "recipient_user_id", nullable = false)
	private String recipientUserId;

	@Column(name = "type", nullable = false, length = 40)
	private String type;

	@Column(name = "channel", nullable = false, length = 10)
	private String channel;

	@Column(name = "status", nullable = false, length = 20)
	private String status;

	// Contact snapshot - stored at creation time (GDPR: contact at notification time)
	@Column(name = "recipient_email", nullable = false)
	private String recipientEmail;

	@Column(name = "recipient_phone")
	private String recipientPhone;

	@Column(name = "recipient_fcm_token", columnDefinition = "TEXT")
	private String recipientFcmToken;

	// Payload
	@Column(name = "title", nullable = false)
	private String title;

	@Column(name = "body", nullable = false, columnDefinition = "TEXT")
	private String body;

	/**
	 * Template data stored as JSONB - avoids EAV anti-pattern for a variable
	 * number of template variables per notification type.
	 * Queried rarely (only for re-render on retry) - JSONB overhead acceptable.
	 */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "template_data", columnDefinition = "jsonb")
	private Map<String, String> templateData;

	@Column(name = "failure_reason", columnDefinition = "TEXT")
	private String failureReason;

	@Column(name = "retry_count", nullable = false)
	private int retryCount;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(name = "version", nullable = false)
	private long version;
}