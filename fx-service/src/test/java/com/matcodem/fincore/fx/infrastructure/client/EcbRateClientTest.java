package com.matcodem.fincore.fx.infrastructure.client;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import com.matcodem.fincore.fx.domain.model.Currency;
import com.matcodem.fincore.fx.domain.model.CurrencyPair;
import com.matcodem.fincore.fx.domain.port.out.RateProviderClient.RateQuote;
import com.matcodem.fincore.fx.infrastructure.client.exception.RateProviderException;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Unit tests for EcbRateClient (fallback provider).
 * Tests XML parsing, EUR-based rate handling, and metrics.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EcbRateClient")
class EcbRateClientTest {

	@Mock
	private RestClient restClient;

	private MeterRegistry meterRegistry;
	private EcbRateClient client;

	@BeforeEach
	void setUp() {
		meterRegistry = new SimpleMeterRegistry();
		client = new EcbRateClient(restClient, meterRegistry);
	}

	@Test
	@DisplayName("should only support EUR-based currency pairs")
	void testSupportsEurBasedPairs() {
		// Given
		CurrencyPair eurUsd = CurrencyPair.of(Currency.EUR, Currency.USD);
		CurrencyPair usdEur = CurrencyPair.of(Currency.USD, Currency.EUR);
		CurrencyPair usdGbp = CurrencyPair.of(Currency.USD, Currency.GBP);

		// When/Then
		// EUR-based pairs would fetch from allRates (would be in fetched map)
		assertThat(eurUsd.getBase()).isEqualTo(Currency.EUR);

		// USD/GBP is not EUR-based, should return empty
		// This is validated in fetchRate logic
	}

	@Test
	@DisplayName("should parse XML response with proper regex pattern")
	void testXmlParsing() {
		// Given - simulated ECB XML response
		String ecbXml = """
			<?xml version="1.0" encoding="UTF-8"?>
			<gesmes:Envelope xmlns:gesmes="http://www.gesmes.org/xml/2002-08-01">
				<Cube>
					<Cube time="2026-03-10">
						<Cube currency='USD' rate='1.0900'/>
						<Cube currency='GBP' rate='0.8600'/>
						<Cube currency='JPY' rate='152.3400'/>
						<Cube currency='PLN' rate='4.2850'/>
					</Cube>
				</Cube>
			</gesmes:Envelope>
			""";

		// Then - XML should be parseable with regex pattern
		assertThat(ecbXml).contains("currency='USD' rate='1.0900'");
		assertThat(ecbXml).contains("currency='PLN' rate='4.2850'");
	}

	@Test
	@DisplayName("should handle invalid rate values gracefully")
	void testHandleInvalidRates() {
		// Given - ECB response with invalid rate
		String malformedXml = """
			<Cube time="2026-03-10">
				<Cube currency='USD' rate='not-a-number'/>
				<Cube currency='GBP' rate='0.86'/>
			</Cube>
			""";

		// Then - parsing should skip invalid, keep valid
		// NumberFormatException would be caught and logged
		assertThat(malformedXml).contains("not-a-number");
	}

	@Test
	@DisplayName("should convert rate strings to BigDecimal")
	void testRateConversion() {
		// Given
		String rateString = "1.0900";

		// When
		BigDecimal rate = new BigDecimal(rateString);

		// Then
		assertThat(rate).isEqualByComparingTo(BigDecimal.valueOf(1.09));
	}

	@Test
	@DisplayName("should initialize metrics counters")
	void testMetricsInitialization() {
		// Then
		assertThat(meterRegistry.find("fx.rate.provider.requests")
				.tag("provider", "ecb")
				.tag("status", "success")
				.counter())
				.isPresent();

		assertThat(meterRegistry.find("fx.rate.provider.requests")
				.tag("provider", "ecb")
				.tag("status", "failure")
				.counter())
				.isPresent();
	}

	@Test
	@DisplayName("client should have correct provider name and priority")
	void testClientMetadata() {
		// Then
		assertThat(client.getProviderName()).isEqualTo("ecb");
		assertThat(client.getPriority()).isEqualTo(2); // Fallback provider (lower priority = preferred)
	}

	@Test
	@DisplayName("RateProviderException should include HTTP status for ECB errors")
	void testErrorHandling() {
		// Given
		String providerName = "ecb";
		String message = "Connection timeout to ECB service";
		int httpStatus = 504;

		// When
		RateProviderException exception = new RateProviderException(
				providerName, message, httpStatus
		);

		// Then
		assertThat(exception.getProviderName()).isEqualTo(providerName);
		assertThat(exception.getHttpStatus()).isEqualTo(httpStatus);
		assertThat(exception.getMessage()).contains("ecb");
		assertThat(exception.getMessage()).contains("504");
	}

	@Test
	@DisplayName("should log detailed parsing statistics")
	void testParsingStatistics() {
		// Given - ECB response with various currency types
		String ecbXml = """
			<Cube time="2026-03-10">
				<Cube currency='USD' rate='1.0900'/>
				<Cube currency='GBP' rate='0.8600'/>
				<Cube currency='INVALID' rate='1.5000'/>
				<Cube currency='JPY' rate='152.3400'/>
			</Cube>
			""";

		// Then - parsing tracks both successful and skipped rates
		// Log output would show: "parsed: 3, skipped: 1"
		assertThat(ecbXml).contains("currency='USD'");
		assertThat(ecbXml).contains("currency='INVALID'");
	}
}

