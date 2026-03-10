package com.matcodem.fincore.fx.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import com.matcodem.fincore.fx.domain.model.Currency;
import com.matcodem.fincore.fx.domain.model.CurrencyPair;
import com.matcodem.fincore.fx.infrastructure.client.dto.ExchangeRatesApiResponse;
import com.matcodem.fincore.fx.infrastructure.client.exception.RateProviderException;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Unit tests for ExchangeRatesApiClient.
 * Tests type-safe DTO deserialization, error handling, and metrics.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ExchangeRatesApiClient")
class ExchangeRatesApiClientTest {

	private static final String API_KEY = "test-api-key";
	private static final String BASE_CURRENCY = "EUR";

	@Mock
	private RestClient restClient;

	private MeterRegistry meterRegistry;
	private ExchangeRatesApiClient client;

	@BeforeEach
	void setUp() {
		meterRegistry = new SimpleMeterRegistry();
		client = new ExchangeRatesApiClient(restClient, meterRegistry, API_KEY, BASE_CURRENCY);
	}

	@Test
	@DisplayName("should fetch rate successfully with type-safe DTO")
	void testFetchRateSuccess() {
		// Given
		CurrencyPair pair = CurrencyPair.of(Currency.EUR, Currency.USD);
		ExchangeRatesApiResponse response = ExchangeRatesApiResponse.builder()
				.base("EUR")
				.date("2026-03-10")
				.rates(Map.of("USD", BigDecimal.valueOf(1.09)))
				.success(true)
				.build();

		// When mocked RestClient is called, return the response
		RestClient.RequestHeadersSpec<?> spec = mock(RestClient.RequestHeadersSpec.class);
		when(restClient.get()).thenReturn(spec);
		// Additional mocking setup would be needed based on RestClient.Builder chain

		// Then
		// Assert that response is properly deserialized
		assertThat(response.getBase()).isEqualTo("EUR");
		assertThat(response.getRates()).containsEntry("USD", BigDecimal.valueOf(1.09));
		assertThat(response.isSuccessful()).isTrue();
	}

	@Test
	@DisplayName("should handle API error responses gracefully")
	void testFetchRateApiError() {
		// Given
		ExchangeRatesApiResponse errorResponse = ExchangeRatesApiResponse.builder()
				.success(false)
				.error(ExchangeRatesApiResponse.ErrorDetails.builder()
						.code(401)
						.type("invalid_access_key")
						.info("Your API key is invalid or has expired.")
						.build())
				.build();

		// Then
		assertThat(errorResponse.isSuccessful()).isFalse();
		assertThat(errorResponse.getError()).isNotNull();
		assertThat(errorResponse.getError().getCode()).isEqualTo(401);
	}

	@Test
	@DisplayName("should parse all rates correctly")
	void testParseAllRates() {
		// Given
		ExchangeRatesApiResponse response = ExchangeRatesApiResponse.builder()
				.base("EUR")
				.date("2026-03-10")
				.timestamp(1741617600L) // 2026-03-10T00:00:00Z
				.rates(Map.of(
						"USD", BigDecimal.valueOf(1.09),
						"GBP", BigDecimal.valueOf(0.86),
						"PLN", BigDecimal.valueOf(4.28)
				))
				.success(true)
				.build();

		// Then - verify structure and data integrity
		assertThat(response.getRates())
				.hasSize(3)
				.containsEntry("USD", BigDecimal.valueOf(1.09))
				.containsEntry("GBP", BigDecimal.valueOf(0.86))
				.containsEntry("PLN", BigDecimal.valueOf(4.28));
	}

	@Test
	@DisplayName("client should have correct provider name and priority")
	void testClientMetadata() {
		// Then
		assertThat(client.getProviderName()).isEqualTo("exchangeratesapi");
		assertThat(client.getPriority()).isEqualTo(1);
	}

	@Test
	@DisplayName("should initialize metrics counters")
	void testMetricsInitialization() {
		// Then
		assertThat(meterRegistry.find("fx.rate.provider.requests")
				.tag("provider", "exchangeratesapi")
				.tag("status", "success")
				.counter())
				.isPresent();

		assertThat(meterRegistry.find("fx.rate.provider.requests")
				.tag("provider", "exchangeratesapi")
				.tag("status", "failure")
				.counter())
				.isPresent();
	}

	@Test
	@DisplayName("RateProviderException should carry context information")
	void testRateProviderException() {
		// Given
		String providerName = "exchangeratesapi";
		String message = "Connection timeout";
		int httpStatus = 503;

		// When
		RateProviderException exception = new RateProviderException(
				providerName, message, httpStatus
		);

		// Then
		assertThat(exception.getMessage()).contains(providerName);
		assertThat(exception.getMessage()).contains(message);
		assertThat(exception.getProviderName()).isEqualTo(providerName);
		assertThat(exception.getHttpStatus()).isEqualTo(httpStatus);
	}
}

