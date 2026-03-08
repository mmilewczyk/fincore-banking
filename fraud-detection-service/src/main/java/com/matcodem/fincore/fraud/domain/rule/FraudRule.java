package com.matcodem.fincore.fraud.domain.rule;

import com.matcodem.fincore.fraud.domain.model.PaymentContext;
import com.matcodem.fincore.fraud.domain.model.RuleResult;

/**
 * Each rule is a self-contained unit of fraud detection logic.
 * Rules are composed by the FraudRuleEngine into a pipeline.
 * <p>
 * Design principles:
 * - Each rule is responsible for ONE specific fraud signal
 * - Rules are independent - no shared mutable state
 * - Rules are ordered by priority (cheaper/faster rules first)
 * - Rules can be enabled/disabled per environment via configuration
 */
public interface FraudRule {

	/**
	 * Evaluates the rule against the payment context.
	 *
	 * @return RuleResult with score and explanation
	 */
	RuleResult evaluate(PaymentContext context);

	/**
	 * Human-readable rule name for audit log and dashboards.
	 */
	String getName();

	/**
	 * Execution order - lower = evaluated first.
	 * Fast/cheap rules should run before slow/expensive ones.
	 */
	int getOrder();

	/**
	 * Whether this rule is currently active.
	 * Allows runtime toggling without redeployment.
	 */
	default boolean isEnabled() {
		return true;
	}
}
