package com.matcodem.fincore.fraud.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.matcodem.fincore.fraud.domain.model.FraudCase;
import com.matcodem.fincore.fraud.domain.model.RiskScore;
import com.matcodem.fincore.fraud.domain.model.RuleResult;
import com.matcodem.fincore.fraud.infrastructure.persistence.mapper.FraudCasePersistenceMapper;

@DisplayName("FraudCasePersistenceMapper")
class FraudCasePersistenceMapperTest {

	private final FraudCasePersistenceMapper mapper = new FraudCasePersistenceMapper();

	@Test
	@DisplayName("should round-trip FraudCase through entity mapping without data loss")
	void shouldRoundTripFraudCase() {
		List<RuleResult> ruleResults = List.of(
				RuleResult.trigger("LARGE_AMOUNT", 40, "Amount exceeds limit"),
				RuleResult.pass("VELOCITY_CHECK"),
				RuleResult.trigger("NEW_ACCOUNT", 10, "Account is new")
		);

		FraudCase original = FraudCase.evaluate(
				"payment-123",
				"acc-source",
				"user-456",
				RiskScore.of(50),
				ruleResults
		);
		original.pullDomainEvents(); // clear events

		// domain → entity → domain
		var entity = mapper.toEntity(original);
		var reconstituted = mapper.toDomain(entity);

		assertThat(reconstituted.getId()).isEqualTo(original.getId());
		assertThat(reconstituted.getPaymentId()).isEqualTo("payment-123");
		assertThat(reconstituted.getSourceAccountId()).isEqualTo("acc-source");
		assertThat(reconstituted.getCompositeScore()).isEqualTo(RiskScore.of(50));
		assertThat(reconstituted.getStatus()).isEqualTo(original.getStatus());
		assertThat(reconstituted.getRuleResults()).hasSize(3);
	}

	@Test
	@DisplayName("should persist all rule results including non-triggered ones")
	void shouldPersistAllRuleResults() {
		List<RuleResult> rules = List.of(
				RuleResult.pass("RULE_A"),
				RuleResult.trigger("RULE_B", 20, "Triggered"),
				RuleResult.pass("RULE_C")
		);

		FraudCase fraudCase = FraudCase.evaluate("pay-001", "acc-001", "user-001",
				RiskScore.of(20), rules);

		var entity = mapper.toEntity(fraudCase);

		assertThat(entity.getRuleResults()).hasSize(3);
		assertThat(entity.getRuleResults())
				.anyMatch(r -> r.getRuleName().equals("RULE_B") && r.isTriggered());
		assertThat(entity.getRuleResults())
				.anyMatch(r -> r.getRuleName().equals("RULE_A") && !r.isTriggered());
	}

	@Test
	@DisplayName("should map risk level from composite score")
	void shouldMapRiskLevel() {
		FraudCase highRisk = FraudCase.evaluate("pay-hr", "acc", "user",
				RiskScore.of(70), List.of());

		var entity = mapper.toEntity(highRisk);

		assertThat(entity.getRiskLevel()).isEqualTo("HIGH");
		assertThat(entity.getCompositeScore()).isEqualTo(70);
	}
}
