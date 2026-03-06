package com.matcodem.fincore.fraud.domain.rule;

import org.springframework.stereotype.Component;

import com.matcodem.fincore.fraud.domain.model.PaymentContext;
import com.matcodem.fincore.fraud.domain.model.RuleResult;

/**
 * Rule: Unusual Currency.
 * <p>
 * A transaction in a currency the user has never used before
 * combined with a high amount is a risk signal.
 * <p>
 * Score contribution: +20 points
 */
@Component
public class UnusualCurrencyRule implements FraudRule {

	@Override
	public RuleResult evaluate(PaymentContext ctx) {
		var behavior = ctx.getUserBehavior();
		if (behavior == null || behavior.mostFrequentCurrency() == null) {
			return RuleResult.pass(getName());
		}

		String txCurrency = ctx.getCurrency();
		String usualCurrency = behavior.mostFrequentCurrency();

		// Flag if transaction currency differs from user's usual currency
		if (!txCurrency.equalsIgnoreCase(usualCurrency) && !behavior.hasInternationalTransactions()) {
			return RuleResult.trigger(getName(), 20,
					"Transaction in %s but user's typical currency is %s with no international history"
							.formatted(txCurrency, usualCurrency));
		}

		return RuleResult.pass(getName());
	}

	@Override
	public String getName() {
		return "UNUSUAL_CURRENCY";
	}

	@Override
	public int getOrder() {
		return 40;
	}
}
