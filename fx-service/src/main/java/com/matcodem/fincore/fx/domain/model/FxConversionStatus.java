package com.matcodem.fincore.fx.domain.model;

public enum FxConversionStatus {
	EXECUTED,  // rate locked, amount converted - immutable financial commitment
	FAILED     // conversion failed (stale rate, provider unavailable, etc.)
}