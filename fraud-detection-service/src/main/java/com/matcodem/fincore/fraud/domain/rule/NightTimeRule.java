package com.matcodem.fincore.fraud.domain.rule;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.matcodem.fincore.fraud.domain.model.PaymentContext;
import com.matcodem.fincore.fraud.domain.model.RuleResult;

/**
 * Rule: Off-Hours Large Transaction.
 * <p>
 * Large transactions between midnight and 5 AM (local bank time)
 * have elevated fraud risk - humans rarely initiate large legitimate
 * payments at 3 AM.
 * <p>
 * Score contribution: +15 points (weak signal, used in combination)
 */
@Component
public class NightTimeRule implements FraudRule {

	@Value("${fraud.rules.night-time.start-hour:0}")
	private int nightStart;

	@Value("${fraud.rules.night-time.end-hour:5}")
	private int nightEnd;

	@Value("${fraud.rules.night-time.amount-threshold:10000}")
	private BigDecimal nightAmountThreshold;

	@Value("${fraud.rules.night-time.timezone:Europe/Warsaw}")
	private String timezone;

	@Override
	public RuleResult evaluate(PaymentContext ctx) {
		ZonedDateTime txTime = ctx.getInitiatedAt()
				.atZone(ZoneId.of(timezone));
		int hour = txTime.getHour();

		boolean isNightTime = hour >= nightStart && hour < nightEnd;
		boolean isHighAmount = ctx.getAmount().compareTo(nightAmountThreshold) > 0;

		if (isNightTime && isHighAmount) {
			return RuleResult.trigger(getName(), 15,
					"Large transaction of %s initiated at %02d:%02d (off-hours: %d-%d)"
							.formatted(ctx.getAmount(), hour, txTime.getMinute(), nightStart, nightEnd));
		}

		return RuleResult.pass(getName());
	}

	@Override
	public String getName() {
		return "OFF_HOURS_LARGE_TX";
	}

	@Override
	public int getOrder() {
		return 50;
	}
}
