package com.matcodem.fincore.fx.adapter.in.web.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ConvertRequest(
		@NotBlank String paymentId,
		@NotBlank String accountId,
		@NotBlank String pair,
		@NotNull @DecimalMin("0.01") BigDecimal sourceAmount,
		@NotBlank String direction
) {
}
