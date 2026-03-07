package com.matcodem.fincore.fx.adapter.in.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ExchangeRateResponse(
		String id, String pair,
		BigDecimal midRate, BigDecimal bidRate, BigDecimal askRate,
		int spreadBasisPoints, String source,
		Instant fetchedAt, Instant validUntil, boolean active
) {
}
