package com.matcodem.fincore.account.adapter.in.web.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for debit/credit operations.
 * Called internally by Payment Service - not part of the public API.
 */
public record BalanceOperationRequest(
		@NotNull @DecimalMin("0.01") BigDecimal amount,
		@NotBlank String currency,
		@NotBlank String reference
) {
}