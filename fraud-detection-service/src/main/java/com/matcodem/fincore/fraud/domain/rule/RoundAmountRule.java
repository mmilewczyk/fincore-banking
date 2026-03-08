package com.matcodem.fincore.fraud.domain.rule;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import com.matcodem.fincore.fraud.domain.model.PaymentContext;
import com.matcodem.fincore.fraud.domain.model.RuleResult;

/**
 * Rule: Suspicious Round Amount.
 * <p>
 * Fraudulent transactions often use round numbers (1000.00, 5000.00)
 * while legitimate payments have odd amounts (1247.53, 389.99).
 * <p>
 * This is a weak signal used in combination with others.
 * Score contribution: +10 points
 */
@Component
public class RoundAmountRule implements FraudRule {

	private static final BigDecimal[] SUSPICIOUS_ROUND_THRESHOLDS = {
			BigDecimal.valueOf(1000),
			BigDecimal.valueOf(2000),
			BigDecimal.valueOf(5000),
			BigDecimal.valueOf(10000)
	};

	@Override
	public RuleResult evaluate(PaymentContext ctx) {
		BigDecimal amount = ctx.getAmount();

		// Check if amount is a "perfectly round" number above 1000
		boolean isRound = amount.remainder(BigDecimal.valueOf(100))
				.compareTo(BigDecimal.ZERO) == 0;

		if (isRound && amount.compareTo(BigDecimal.valueOf(1000)) >= 0) {
			return RuleResult.trigger(getName(), 10,
					"Suspiciously round amount: %s - commonly seen in structured fraud"
							.formatted(amount));
		}

		return RuleResult.pass(getName());
	}

	@Override
	public String getName() {
		return "ROUND_AMOUNT";
	}

	@Override
	public int getOrder() {
		return 60;
	}
}
