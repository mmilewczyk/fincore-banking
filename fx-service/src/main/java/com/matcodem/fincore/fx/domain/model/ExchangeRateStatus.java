package com.matcodem.fincore.fx.domain.model;

public enum ExchangeRateStatus {
	ACTIVE,       // current, usable for conversions
	SUPERSEDED,   // replaced by a newer rate
	STALE         // validity window expired (managed by scheduler)
}