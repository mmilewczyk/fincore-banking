package com.matcodem.fincore.payment.adapter.in.web.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record InitiatePaymentRequest(
		@NotBlank(message = "Source account required")
		String sourceAccountId,

		@NotBlank(message = "Target account required")
		String targetAccountId,

		@NotNull(message = "Amount required")
		@DecimalMin(value = "0.01", message = "Amount must be positive")
		@Digits(integer = 15, fraction = 2, message = "Invalid amount format")
		BigDecimal amount,

		@NotBlank(message = "Currency required")
		@Size(min = 3, max = 3, message = "Currency must be 3 characters")
		String currency,

		@NotBlank(message = "Payment type required")
		String type
) {}