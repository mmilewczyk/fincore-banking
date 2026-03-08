package com.matcodem.fincore.fraud.domain.rule;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.matcodem.fincore.fraud.domain.model.PaymentContext;
import com.matcodem.fincore.fraud.domain.model.RuleResult;

/**
 * Rule: Velocity Check - too many transactions in a short window.
 * <p>
 * A burst of transactions is a classic card-testing or account-takeover signal.
 * <p>
 * Score contribution:
 * - More than 10 transactions in last hour:        +50 points (CRITICAL signal)
 * - More than 5 transactions in last hour:         +25 points
 * - More than 20 transactions in last 24 hours:    +20 points
 */
@Component
public class VelocityRule implements FraudRule {

	@Value("${fraud.rules.velocity.max-per-hour-critical:10}")
	private int maxPerHourCritical;

	@Value("${fraud.rules.velocity.max-per-hour-warn:5}")
	private int maxPerHourWarn;

	@Value("${fraud.rules.velocity.max-per-day:20}")
	private int maxPerDay;

	@Override
	public RuleResult evaluate(PaymentContext ctx) {
		var behavior = ctx.getUserBehavior();
		if (behavior == null) return RuleResult.pass(getName());

		int lastHour = behavior.transactionsLast1Hour();
		int last24h = behavior.transactionsLast24Hours();

		if (lastHour >= maxPerHourCritical) {
			return RuleResult.trigger(getName(), 50,
					"%d transactions in the last hour (limit: %d) - possible card testing attack"
							.formatted(lastHour, maxPerHourCritical));
		}

		if (lastHour >= maxPerHourWarn) {
			return RuleResult.trigger(getName(), 25,
					"%d transactions in the last hour (warn threshold: %d)"
							.formatted(lastHour, maxPerHourWarn));
		}

		if (last24h >= maxPerDay) {
			return RuleResult.trigger(getName(), 20,
					"%d transactions in the last 24 hours (limit: %d)"
							.formatted(last24h, maxPerDay));
		}

		return RuleResult.pass(getName());
	}

	@Override
	public String getName() {
		return "VELOCITY_CHECK";
	}

	@Override
	public int getOrder() {
		return 20;
	}
}