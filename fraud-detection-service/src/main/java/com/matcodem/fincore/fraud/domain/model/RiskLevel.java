package com.matcodem.fincore.fraud.domain.model;

public enum RiskLevel {
	LOW,      // 0–29:  approve automatically
	MEDIUM,   // 30–59: block payment, queue for review
	HIGH,     // 60–84: block payment, freeze account, alert compliance
	CRITICAL  // 85+:   block + freeze + immediate escalation
}