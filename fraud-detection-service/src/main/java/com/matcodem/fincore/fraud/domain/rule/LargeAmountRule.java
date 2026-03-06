package com.matcodem.fincore.fraud.domain.rule;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.matcodem.fincore.fraud.domain.model.PaymentContext;
import com.matcodem.fincore.fraud.domain.model.RuleResult;

/**
 * Rule: Large Amount Detection.
 * <p>
 * Payments significantly above the user's historical average
 * or above absolute thresholds are flagged.
 * <p>
 * Score contribution:
 * - Above absolute limit (50k PLN):            +40 points
 * - 10x user's largest historical transaction: +30 points
 * - 3x user's average transaction:             +15 points
 */
@Component
public class LargeAmountRule implements FraudRule {

	@Value("${fraud.rules.large-amount.absolute-limit:50000}")
	private BigDecimal absoluteLimit;

	@Value("${fraud.rules.large-amount.historical-multiplier:10}")
	private int historicalMultiplier;

	@Value("${fraud.rules.large-amount.average-multiplier:3}")
	private int averageMultiplier;

	@Override
	public RuleResult evaluate(PaymentContext ctx) {
		BigDecimal amount = ctx.getAmount();

		// Absolute threshold check
		if (amount.compareTo(absoluteLimit) > 0) {
			return RuleResult.trigger(getName(), 40,
					"Amount %s exceeds absolute limit of %s %s"
							.formatted(amount, absoluteLimit, ctx.getCurrency()));
		}

		// Behavioral: compare to user's history
		var behavior = ctx.getUserBehavior();
		if (behavior != null && behavior.largestTransactionEver() != null) {

			BigDecimal largest = behavior.largestTransactionEver();
			if (!largest.equals(BigDecimal.ZERO) &&
					amount.compareTo(largest.multiply(BigDecimal.valueOf(historicalMultiplier))) > 0) {
				return RuleResult.trigger(getName(), 30,
						"Amount %s is %dx larger than user's historical max of %s"
								.formatted(amount, historicalMultiplier, largest));
			}

			BigDecimal avg = behavior.totalAmountLast24Hours();
			if (!avg.equals(BigDecimal.ZERO) &&
					amount.compareTo(avg.multiply(BigDecimal.valueOf(averageMultiplier))) > 0) {
				return RuleResult.trigger(getName(), 15,
						"Amount %s is %dx user's recent average of %s"
								.formatted(amount, averageMultiplier, avg));
			}
		}

		return RuleResult.pass(getName());
	}

	@Override
	public String getName() {
		return "LARGE_AMOUNT";
	}

	@Override
	public int getOrder() {
		return 10;
	}
}
