package com.matcodem.fincore.fraud.infrastructure.persistence.mapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.matcodem.fincore.fraud.domain.model.FraudCase;
import com.matcodem.fincore.fraud.domain.model.FraudCaseId;
import com.matcodem.fincore.fraud.domain.model.FraudCaseStatus;
import com.matcodem.fincore.fraud.domain.model.RiskScore;
import com.matcodem.fincore.fraud.domain.model.RuleResult;
import com.matcodem.fincore.fraud.infrastructure.persistence.entity.FraudCaseJpaEntity;
import com.matcodem.fincore.fraud.infrastructure.persistence.entity.FraudRuleResultJpaEntity;

/**
 * Maps between JPA entities and domain aggregates.
 * Keeps domain model free of persistence concerns.
 */
@Component
public class FraudCasePersistenceMapper {

	public FraudCase toDomain(FraudCaseJpaEntity entity) {
		List<RuleResult> ruleResults = entity.getRuleResults().stream()
				.map(this::ruleResultToDomain)
				.toList();

		return FraudCase.reconstitute(
				FraudCaseId.of(entity.getId()),
				entity.getPaymentId(),
				entity.getSourceAccountId(),
				entity.getInitiatedBy(),
				RiskScore.of(entity.getCompositeScore()),
				FraudCaseStatus.valueOf(entity.getStatus()),
				ruleResults,
				entity.getReviewedBy(),
				entity.getReviewNotes(),
				entity.getCreatedAt(),
				entity.getUpdatedAt(),
				entity.getVersion()
		);
	}

	public FraudCaseJpaEntity toEntity(FraudCase domain) {
		FraudCaseJpaEntity entity = new FraudCaseJpaEntity();
		entity.setId(domain.getId().value());
		entity.setPaymentId(domain.getPaymentId());
		entity.setSourceAccountId(domain.getSourceAccountId());
		entity.setInitiatedBy(domain.getInitiatedBy());
		entity.setCompositeScore(domain.getCompositeScore().getValue());
		entity.setRiskLevel(domain.getCompositeScore().getLevel().name());
		entity.setStatus(domain.getStatus().name());
		entity.setReviewedBy(domain.getReviewedBy());
		entity.setReviewNotes(domain.getReviewNotes());
		entity.setCreatedAt(domain.getCreatedAt());
		entity.setUpdatedAt(domain.getUpdatedAt());

		List<FraudRuleResultJpaEntity> ruleEntities = domain.getRuleResults().stream()
				.map(r -> ruleResultToEntity(r, entity))
				.toList();
		entity.getRuleResults().clear();
		entity.getRuleResults().addAll(ruleEntities);

		return entity;
	}

	private RuleResult ruleResultToDomain(FraudRuleResultJpaEntity e) {
		return new RuleResult(
				e.getRuleName(),
				RiskScore.of(e.getScore()),
				e.isTriggered(),
				e.getReason()
		);
	}

	private FraudRuleResultJpaEntity ruleResultToEntity(RuleResult r, FraudCaseJpaEntity parent) {
		FraudRuleResultJpaEntity e = new FraudRuleResultJpaEntity();
		e.setId(UUID.randomUUID());
		e.setFraudCase(parent);
		e.setRuleName(r.ruleName());
		e.setScore(r.score().getValue());
		e.setTriggered(r.triggered());
		e.setReason(r.reason());
		e.setEvaluatedAt(Instant.now());
		return e;
	}
}
