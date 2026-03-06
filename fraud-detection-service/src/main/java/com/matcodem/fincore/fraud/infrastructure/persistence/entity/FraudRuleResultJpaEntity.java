package com.matcodem.fincore.fraud.infrastructure.persistence.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "fraud_rule_results", indexes = {
		@Index(name = "idx_rule_results_case_id", columnList = "fraud_case_id"),
		@Index(name = "idx_rule_results_rule_name", columnList = "rule_name"),
		@Index(name = "idx_rule_results_triggered", columnList = "triggered")
})
@Getter
@Setter
@NoArgsConstructor
public class FraudRuleResultJpaEntity {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "fraud_case_id", nullable = false)
	private FraudCaseJpaEntity fraudCase;

	@Column(name = "rule_name", nullable = false, length = 50)
	private String ruleName;

	@Column(name = "score", nullable = false)
	private int score;

	@Column(name = "triggered", nullable = false)
	private boolean triggered;

	@Column(name = "reason", nullable = false, columnDefinition = "TEXT")
	private String reason;

	@Column(name = "evaluated_at", nullable = false)
	private Instant evaluatedAt;
}