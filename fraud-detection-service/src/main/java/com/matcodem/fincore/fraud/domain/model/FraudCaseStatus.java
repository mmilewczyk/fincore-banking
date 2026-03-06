package com.matcodem.fincore.fraud.domain.model;

public enum FraudCaseStatus {
	APPROVED,         // Low risk — payment proceeds
	BLOCKED,          // High/Critical risk — payment blocked, account possibly frozen
	UNDER_REVIEW,     // Medium risk — queued for manual compliance review
	CONFIRMED_FRAUD   // Manually confirmed as fraud
}