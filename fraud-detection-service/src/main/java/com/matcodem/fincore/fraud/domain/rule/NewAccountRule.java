package com.matcodem.fincore.fraud.domain.rule;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.matcodem.fincore.fraud.domain.model.PaymentContext;
import com.matcodem.fincore.fraud.domain.model.RuleResult;

/**
 * Rule: New Account Detection.
 * <p>
 * Newly created accounts are a common vector for money mule schemes.
 * High-value transactions from new accounts are flagged.
 * <p>
 * Score contribution:
 * - New source account + amount > 5000:     +35 points
 * - New source account + any transaction:   +10 points
 * - New target account + high amount:       +20 points
 */
@Component
public class NewAccountRule implements FraudRule {

	@Value("${fraud.rules.new-account.high-amount-threshold:5000}")
	private BigDecimal highAmountThreshold;

	@Override
	public RuleResult evaluate(PaymentContext ctx) {
		var source = ctx.getSourceAccount();
		var target = ctx.getTargetAccount();
		BigDecimal amount = ctx.getAmount();

		if (source != null && source.isNew()) {
			if (amount.compareTo(highAmountThreshold) > 0) {
				return RuleResult.trigger(getName(), 35,
						"Source account is new (<30 days) and amount %s exceeds threshold %s"
								.formatted(amount, highAmountThreshold));
			}
			return RuleResult.trigger(getName(), 10,
					"Source account is new (<30 days) - elevated risk");
		}

		if (target != null && target.isNew() && amount.compareTo(highAmountThreshold) > 0) {
			return RuleResult.trigger(getName(), 20,
					"Target account is new (<30 days) receiving high amount %s"
							.formatted(amount));
		}

		return RuleResult.pass(getName());
	}

	@Override
	public String getName() {
		return "NEW_ACCOUNT";
	}

	@Override
	public int getOrder() {
		return 30;
	}
}
