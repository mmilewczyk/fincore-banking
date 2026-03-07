package com.matcodem.fincore.fraud.adapter.in.web.dto;

import java.time.Instant;
import java.util.List;

import com.matcodem.fincore.fraud.domain.model.FraudCase;

public record FraudCaseResponse(
		String id,
		String paymentId,
		String sourceAccountId,
		int riskScore,
		String riskLevel,
		String status,
		List<RuleResultResponse> ruleResults,
		String reviewedBy,
		String reviewNotes,
		Instant createdAt,
		Instant updatedAt
) {

	public static FraudCaseResponse toResponse(FraudCase fc) {
		return new FraudCaseResponse(
				fc.getId().toString(),
				fc.getPaymentId(),
				fc.getSourceAccountId(),
				fc.getCompositeScore().getValue(),
				fc.getCompositeScore().getLevel().name(),
				fc.getStatus().name(),
				fc.getRuleResults().stream().map(r -> new RuleResultResponse(
						r.ruleName(), r.score().getValue(), r.triggered(), r.reason()
				)).toList(),
				fc.getReviewedBy(),
				fc.getReviewNotes(),
				fc.getCreatedAt(),
				fc.getUpdatedAt()
		);
	}
}