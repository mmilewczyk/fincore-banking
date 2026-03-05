package com.matcodem.fincore.account.adapter.in.web.dto;

import com.matcodem.fincore.account.domain.model.Currency;

import jakarta.validation.constraints.NotNull;

public record OpenAccountRequest(
		@NotNull(message = "Currency is required")
		Currency currency
) {
}
