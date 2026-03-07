package com.matcodem.fincore.fraud.adapter.in.web.dto;

public record RuleResultResponse(
		String ruleName,
		int score,
		boolean triggered,
		String reason
) {
}
