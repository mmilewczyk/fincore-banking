package com.matcodem.fincore.account.adapter.in.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.matcodem.fincore.account.domain.model.AccountStatus;
import com.matcodem.fincore.account.domain.model.Currency;

public record AccountResponse(
		String id,
		String ownerId,
		String iban,
		Currency currency,
		BigDecimal balance,
		AccountStatus status,
		Instant createdAt,
		Instant updatedAt
) {
}
