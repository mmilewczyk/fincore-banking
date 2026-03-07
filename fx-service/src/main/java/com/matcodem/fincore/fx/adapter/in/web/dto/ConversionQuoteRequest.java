package com.matcodem.fincore.fx.adapter.in.web.dto;


import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ConversionQuoteRequest(
		@NotBlank String pair,
		@NotNull @DecimalMin("0.01") BigDecimal amount,
		@NotBlank String direction
) {
}