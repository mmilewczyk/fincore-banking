package com.matcodem.fincore.fraud.domain.service;


import static com.matcodem.fincore.fraud.domain.model.RiskLevel.CRITICAL;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import com.matcodem.fincore.fraud.domain.model.PaymentContext;
import com.matcodem.fincore.fraud.domain.model.RiskScore;
import com.matcodem.fincore.fraud.domain.model.RuleResult;
import com.matcodem.fincore.fraud.domain.rule.FraudRule;

import lombok.extern.slf4j.Slf4j;

/**
 * Fraud Rule Engine - Domain Service.
 * <p>
 * Orchestrates all registered FraudRules into a scoring pipeline:
 * <p>
 * 1. Rules are sorted by getOrder() (cheapest/fastest first)
 * 2. Each enabled rule evaluates the PaymentContext
 * 3. Scores are aggregated using RiskScore.combine() - takes the maximum
 * 4. All triggered rules contribute to the final composite score
 * 5. Short-circuit: if score reaches CRITICAL (85+), skip remaining rules
 * <p>
 * This is a pure domain service - no Spring annotations, no infrastructure.
 * Easily testable in isolation.
 */
@Slf4j
public class FraudRuleEngine {

	private final List<FraudRule> rules;

	public FraudRuleEngine(List<FraudRule> rules) {
		Objects.requireNonNull(rules, "Rules cannot be null");
		this.rules = rules.stream()
				.filter(FraudRule::isEnabled)
				.sorted(Comparator.comparingInt(FraudRule::getOrder))
				.toList();

		log.info("FraudRuleEngine initialized with {} rules: {}",
				this.rules.size(),
				this.rules.stream().map(FraudRule::getName).toList());
	}

	/**
	 * Evaluates all rules against the payment context.
	 * Returns an EvaluationResult with composite score and all rule results.
	 */
	public EvaluationResult evaluate(PaymentContext context) {
		Objects.requireNonNull(context, "PaymentContext cannot be null");

		log.debug("Evaluating fraud rules for payment: {}", context.getPaymentId());

		List<RuleResult> results = new java.util.ArrayList<>();
		RiskScore compositeScore = RiskScore.zero();

		for (FraudRule rule : rules) {
			try {
				RuleResult result = rule.evaluate(context);
				results.add(result);

				if (result.triggered()) {
					compositeScore = compositeScore.add(result.score().getValue());
					log.info("Rule '{}' triggered for payment {} - score: +{}, reason: {}",
							rule.getName(), context.getPaymentId(),
							result.score().getValue(), result.reason());
				}

				// Short-circuit on CRITICAL score - no point evaluating more rules
				if (compositeScore.getLevel() == CRITICAL) {
					log.warn("CRITICAL risk score reached for payment {} - short-circuiting rule evaluation",
							context.getPaymentId());
					break;
				}

			} catch (Exception ex) {
				// Rule evaluation failure must never block payment processing
				// Log the error and treat as non-triggered (score = 0)
				log.error("Rule '{}' threw an exception for payment {}: {}",
						rule.getName(), context.getPaymentId(), ex.getMessage(), ex);
				results.add(RuleResult.pass(rule.getName() + "[ERROR]"));
			}
		}

		log.info("Fraud evaluation complete for payment {} - composite score: {}, level: {}",
				context.getPaymentId(), compositeScore.getValue(), compositeScore.getLevel());

		return new EvaluationResult(compositeScore, results);
	}

	public int getRuleCount() {
		return rules.size();
	}

	public record EvaluationResult(
			RiskScore compositeScore,
			List<RuleResult> ruleResults
	) {
		public boolean isFraudulent() {
			return compositeScore.requiresBlock();
		}
	}
}