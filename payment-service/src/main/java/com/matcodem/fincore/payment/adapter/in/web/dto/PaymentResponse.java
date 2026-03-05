package com.matcodem.fincore.payment.adapter.in.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
		String id,
		String idempotencyKey,
		String sourceAccountId,
		String targetAccountId,
		BigDecimal amount,
		String currency,
		String type,
		String status,
		String failureReason,
		String initiatedBy,
		Instant createdAt,
		Instant updatedAt
) {}
