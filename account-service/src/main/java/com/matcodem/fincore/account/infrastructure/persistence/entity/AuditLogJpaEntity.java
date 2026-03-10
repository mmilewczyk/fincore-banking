package com.matcodem.fincore.account.infrastructure.persistence.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Entity
@Table(name = "audit_log", indexes = {
		@Index(name = "idx_audit_account_id", columnList = "account_id"),
		@Index(name = "idx_audit_occurred_at", columnList = "occurred_at")
})
@Getter
@NoArgsConstructor
public class AuditLogJpaEntity {

	@Id
	private UUID id;

	@Column(name = "account_id", nullable = false)
	private UUID accountId;

	@Column(name = "event_type", nullable = false, length = 50)
	private String eventType;

	@Column(name = "performed_by", nullable = false, length = 100)
	private String performedBy;

	@Column(name = "details", columnDefinition = "TEXT")
	private String details;

	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

}