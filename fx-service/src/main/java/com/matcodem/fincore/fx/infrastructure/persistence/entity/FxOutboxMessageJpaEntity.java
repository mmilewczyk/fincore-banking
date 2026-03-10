package com.matcodem.fincore.fx.infrastructure.persistence.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
		name = "fx_outbox_messages",
		indexes = {
				@Index(name = "idx_fx_outbox_aggregate", columnList = "aggregate_id")
		}
)
@Getter
@Setter
@NoArgsConstructor
public class FxOutboxMessageJpaEntity {

	@Id
	private UUID id;

	@Column(name = "aggregate_id", nullable = false)
	private String aggregateId;

	@Column(name = "aggregate_type", nullable = false, length = 50)
	private String aggregateType;

	@Column(name = "event_type", nullable = false, length = 80)
	private String eventType;

	@Column(name = "payload", nullable = false, columnDefinition = "TEXT")
	private String payload;

	@Column(name = "status", nullable = false, length = 20)
	@Enumerated(EnumType.STRING)
	private Status status;

	@Column(name = "retry_count", nullable = false)
	private int retryCount;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "processed_at")
	private Instant processedAt;

	public enum Status {
		PENDING,
		SENT,
		DEAD
	}
}