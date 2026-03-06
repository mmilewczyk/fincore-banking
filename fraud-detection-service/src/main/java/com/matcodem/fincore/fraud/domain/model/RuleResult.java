package com.matcodem.fincore.fraud.domain.model;

import java.util.Objects;

/**
 * Value Object — result of a single fraud rule evaluation.
 * Immutable record of what a rule decided and why.
 */
public record RuleResult(
		String ruleName,
		RiskScore score,
		boolean triggered,
		String reason
) {
	public RuleResult {
		Objects.requireNonNull(ruleName, "Rule name required");
		Objects.requireNonNull(score, "Score required");
		Objects.requireNonNull(reason, "Reason required");
	}

	public static RuleResult pass(String ruleName) {
		return new RuleResult(ruleName, RiskScore.zero(), false, "No issues detected");
	}

	public static RuleResult trigger(String ruleName, int scorePoints, String reason) {
		return new RuleResult(ruleName, RiskScore.of(scorePoints), true, reason);
	}
}