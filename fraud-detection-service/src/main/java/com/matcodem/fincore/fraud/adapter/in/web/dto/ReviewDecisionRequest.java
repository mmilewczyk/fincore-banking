package com.matcodem.fincore.fraud.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;

public record ReviewDecisionRequest(
		@NotBlank String notes
) {
}
