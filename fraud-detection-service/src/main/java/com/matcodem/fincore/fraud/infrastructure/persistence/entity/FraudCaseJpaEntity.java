package com.matcodem.fincore.fraud.infrastructure.persistence.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "fraud_cases", indexes = {
		@Index(name = "idx_fraud_cases_payment_id", columnList = "payment_id", unique = true),
		@Index(name = "idx_fraud_cases_status", columnList = "status"),
		@Index(name = "idx_fraud_cases_source_acct", columnList = "source_account_id"),
		@Index(name = "idx_fraud_cases_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
public class FraudCaseJpaEntity {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Column(name = "payment_id", nullable = false, unique = true)
	private String paymentId;

	@Column(name = "source_account_id", nullable = false)
	private String sourceAccountId;

	@Column(name = "initiated_by", nullable = false)
	private String initiatedBy;

	@Column(name = "composite_score", nullable = false)
	private int compositeScore;

	@Column(name = "risk_level", nullable = false, length = 20)
	private String riskLevel;

	@Column(name = "status", nullable = false, length = 30)
	private String status;

	@Column(name = "reviewed_by")
	private String reviewedBy;

	@Column(name = "review_notes", columnDefinition = "TEXT")
	private String reviewNotes;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(name = "version", nullable = false)
	private long version;

	@OneToMany(mappedBy = "fraudCase", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
	private List<FraudRuleResultJpaEntity> ruleResults = new ArrayList<>();
}