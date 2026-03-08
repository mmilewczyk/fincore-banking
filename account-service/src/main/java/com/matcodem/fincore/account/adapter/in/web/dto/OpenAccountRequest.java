package com.matcodem.fincore.account.adapter.in.web.dto;

import com.matcodem.fincore.account.domain.model.Currency;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;

public record OpenAccountRequest(
		@NotNull(message = "Currency is required")
		Currency currency,

		@NotNull(message = "Email is required")
		@Email(message = "Email must be a valid address")
		String email,

		// Phone is optional - enables SMS notifications if provided
		String phoneNumber
) {}
