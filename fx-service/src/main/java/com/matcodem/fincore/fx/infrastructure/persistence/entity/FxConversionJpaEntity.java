package com.matcodem.fincore.fx.infrastructure.persistence.entity;

import java.math.BigDecimal;
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

@Entity
@Table(name = "fx_conversions", indexes = {
		@Index(name = "idx_fx_conv_payment", columnList = "payment_id", unique = true),
		@Index(name = "idx_fx_conv_account", columnList = "account_id"),
		@Index(name = "idx_fx_conv_created", columnList = "created_at DESC")
})
@Getter
@Setter
@NoArgsConstructor
public class FxConversionJpaEntity {

	@Id
	private UUID id;

	@Column(name = "payment_id", nullable = false, unique = true)
	private String paymentId;
	@Column(name = "account_id", nullable = false)
	private String accountId;
	@Column(name = "requested_by", nullable = false)
	private String requestedBy;
	@Column(name = "base_currency", nullable = false, length = 3)
	private String baseCurrency;
	@Column(name = "quote_currency", nullable = false, length = 3)
	private String quoteCurrency;
	@Column(name = "source_amount", nullable = false, precision = 19, scale = 4)
	private BigDecimal sourceAmount;
	@Column(name = "converted_amount", nullable = false, precision = 19, scale = 4)
	private BigDecimal convertedAmount;
	@Column(name = "applied_rate", nullable = false, precision = 19, scale = 6)
	private BigDecimal appliedRate;
	@Column(name = "fee", nullable = false, precision = 19, scale = 4)
	private BigDecimal fee;
	@Column(name = "spread_bps", nullable = false)
	private int spreadBasisPoints;
	@Column(name = "rate_snapshot_id")
	private UUID rateSnapshotId;
	@Column(name = "rate_timestamp", nullable = false)
	private Instant rateTimestamp;
	@Column(name = "status", nullable = false, length = 20)
	private String status;
	@Column(name = "failure_reason", columnDefinition = "TEXT")
	private String failureReason;
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;
}