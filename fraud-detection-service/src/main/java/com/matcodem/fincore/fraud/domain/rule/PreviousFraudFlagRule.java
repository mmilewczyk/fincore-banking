package com.matcodem.fincore.fraud.domain.rule;

import org.springframework.stereotype.Component;

import com.matcodem.fincore.fraud.domain.model.PaymentContext;
import com.matcodem.fincore.fraud.domain.model.RuleResult;

/**
 * Rule: Previous Fraud Flag.
 * <p>
 * If either account has a prior fraud flag, all transactions
 * from/to that account carry elevated risk.
 * <p>
 * Score contribution: +45 points (near-block on its own)
 */
@Component
public class PreviousFraudFlagRule implements FraudRule {

	@Override
	public RuleResult evaluate(PaymentContext ctx) {
		var source = ctx.getSourceAccount();
		var target = ctx.getTargetAccount();

		if (source != null && source.hasPreviousFraudFlags()) {
			return RuleResult.trigger(getName(), 45,
					"Source account %s has prior fraud flags"
							.formatted(ctx.getSourceAccountId()));
		}

		if (target != null && target.hasPreviousFraudFlags()) {
			return RuleResult.trigger(getName(), 35,
					"Target account %s has prior fraud flags - possible money mule"
							.formatted(ctx.getTargetAccountId()));
		}

		return RuleResult.pass(getName());
	}

	@Override
	public String getName() {
		return "PREVIOUS_FRAUD_FLAG";
	}

	@Override
	public int getOrder() {
		return 5;
	} // run early - cheap and high-signal
}
