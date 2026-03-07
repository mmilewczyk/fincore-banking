package com.matcodem.fincore.fx.adapter.in.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record FxConversionResponse(
		String id, String paymentId, String pair,
		BigDecimal sourceAmount, BigDecimal convertedAmount,
		BigDecimal appliedRate, BigDecimal fee, int spreadBasisPoints,
		String status, Instant createdAt
) {
}
