package com.matcodem.fincore.fx.infrastructure.client.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for ExchangeRatesAPI.io response.
 * API: GET /latest?base=EUR&symbols=PLN,USD,GBP,...
 * Response: { "base": "EUR", "date": "2024-03-06", "rates": { "PLN": 4.285, ... } }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExchangeRatesApiResponse {

	private String base;
	private String date;
	private Map<String, Number> rates;

	@JsonProperty("success")
	private Boolean success;

	@JsonProperty("timestamp")
	private Long timestamp;

	@JsonProperty("error")
	private ErrorDetails error;

	public boolean isSuccessful() {
		return success == null ? error == null : success;
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	@Builder
	public static class ErrorDetails {
		private Integer code;
		private String type;
		private String info;
	}
}


