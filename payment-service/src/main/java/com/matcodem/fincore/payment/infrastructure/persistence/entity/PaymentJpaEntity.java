package com.matcodem.fincore.payment.infrastructure.persistence.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

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
@Table(name = "payments", indexes = {
		@Index(name = "idx_payments_idempotency_key", columnList = "idempotency_key", unique = true),
		@Index(name = "idx_payments_source_account", columnList = "source_account_id"),
		@Index(name = "idx_payments_target_account", columnList = "target_account_id"),
		@Index(name = "idx_payments_status", columnList = "status"),
		@Index(name = "idx_payments_initiated_by", columnList = "initiated_by")
})
@Getter
@Setter
@NoArgsConstructor
public class PaymentJpaEntity {

	@Id
	private UUID id;

	@Column(name = "idempotency_key", nullable = false, unique = true, length = 64)
	private String idempotencyKey;

	@Column(name = "source_account_id", nullable = false)
	private String sourceAccountId;

	@Column(name = "target_account_id", nullable = false)
	private String targetAccountId;

	@Column(name = "amount", nullable = false, precision = 19, scale = 4)
	private BigDecimal amount;

	@Column(name = "currency", nullable = false, length = 3)
	private String currency;

	@Column(name = "type", nullable = false, length = 30)
	private String type;

	@Column(name = "status", nullable = false, length = 30)
	private String status;

	@Column(name = "failure_reason", columnDefinition = "TEXT")
	private String failureReason;

	@Column(name = "initiated_by", nullable = false)
	private String initiatedBy;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(name = "version", nullable = false)
	private long version;
}