package com.matcodem.fincore.fx.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.matcodem.fincore.fx.domain.model.Currency;
import com.matcodem.fincore.fx.domain.model.CurrencyPair;
import com.matcodem.fincore.fx.domain.model.ExchangeRate;
import com.matcodem.fincore.fx.domain.model.FxConversion;
import com.matcodem.fincore.fx.domain.port.in.ConvertCurrencyUseCase;
import com.matcodem.fincore.fx.domain.port.out.FxConversionRepository;
import com.matcodem.fincore.fx.domain.port.out.FxEventPublisher;
import com.matcodem.fincore.fx.domain.port.out.FxOutboxRepository;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Unit tests for FxConversionService.
 * Tests conversion execution, persistence, and event publishing.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FxConversionService")
class FxConversionServiceTest {

	@Mock
	private FxConversionRepository conversionRepository;

	@Mock
	private FxEventPublisher eventPublisher;

	@Mock
	private FxOutboxRepository outboxRepository;

	@Mock
	private FxRateQueryService rateQueryService;

	private MeterRegistry meterRegistry;
	private FxConversionService service;

	@BeforeEach
	void setUp() {
		meterRegistry = new SimpleMeterRegistry();
		service = new FxConversionService(
				conversionRepository, outboxRepository, rateQueryService, meterRegistry
		);
	}

	@Test
	@DisplayName("convert should execute conversion and publish events on success")
	void testConvertSuccess() {
		// Given
		ConvertCurrencyUseCase.ConvertCommand command = new ConvertCurrencyUseCase.ConvertCommand(
				"PAY-001", "ACC-001", "user@bank.com",
				CurrencyPair.of(Currency.EUR, Currency.USD),
				BigDecimal.valueOf(1000), ExchangeRate.ConversionDirection.BUY_BASE
		);

		ExchangeRate rate = mock(ExchangeRate.class);
		FxConversion conversion = mock(FxConversion.class);
		FxConversion savedConversion = mock(FxConversion.class);

		when(rateQueryService.getRateWithFallback(command.pair())).thenReturn(rate);
		when(conversionRepository.save(any(FxConversion.class))).thenReturn(savedConversion);
		when(savedConversion.pullDomainEvents()).thenReturn(List.of());

		// When
		FxConversion result = service.convert(command);

		// Then
		assertThat(result).isEqualTo(savedConversion);
		verify(rateQueryService).getRateWithFallback(command.pair());
		verify(conversionRepository).save(any(FxConversion.class));
		verify(eventPublisher).publishAll(List.of());
		assertThat(meterRegistry.counter("fx.conversion.success", "pair", command.pair().getSymbol()).count())
				.isEqualTo(1.0);
	}

	@Test
	@DisplayName("convert should persist failed conversion on rate unavailable")
	void testConvertRateUnavailable() {
		// Given
		ConvertCurrencyUseCase.ConvertCommand command = new ConvertCurrencyUseCase.ConvertCommand(
				"PAY-001", "ACC-001", "user@bank.com",
				CurrencyPair.of(Currency.EUR, Currency.USD),
				BigDecimal.valueOf(1000), ExchangeRate.ConversionDirection.BUY_BASE
		);

		when(rateQueryService.getRateWithFallback(command.pair()))
				.thenThrow(new FxRateQueryService.RateUnavailableException("No rates available"));

		FxConversion failedConversion = mock(FxConversion.class);
		when(conversionRepository.save(any(FxConversion.class))).thenReturn(failedConversion);
		when(failedConversion.pullDomainEvents()).thenReturn(List.of());

		// When/Then
		assertThatThrownBy(() -> service.convert(command))
				.isInstanceOf(FxRateQueryService.RateUnavailableException.class);

		// Verify failure was recorded
		ArgumentCaptor<FxConversion> conversionCaptor = ArgumentCaptor.forClass(FxConversion.class);
		verify(conversionRepository).save(conversionCaptor.capture());
		verify(eventPublisher).publishAll(List.of());
		assertThat(meterRegistry.counter("fx.conversion.failed", "pair", command.pair().getSymbol(),
				"reason", "RateUnavailableException").count())
				.isEqualTo(1.0);
	}

	@Test
	@DisplayName("quote should return conversion result without persisting")
	void testQuote() {
		// Given
		CurrencyPair pair = CurrencyPair.of(Currency.EUR, Currency.USD);
		BigDecimal amount = BigDecimal.valueOf(1000);

		ExchangeRate rate = mock(ExchangeRate.class);
		ExchangeRate.ConversionResult result = mock(ExchangeRate.ConversionResult.class);

		when(rateQueryService.getRateWithFallback(pair)).thenReturn(rate);
		when(rate.convert(amount, ExchangeRate.ConversionDirection.BUY_BASE)).thenReturn(result);

		// When
		ExchangeRate.ConversionResult quoteResult = service.quote(pair, amount, ExchangeRate.ConversionDirection.BUY_BASE);

		// Then
		assertThat(quoteResult).isEqualTo(result);
		verify(rateQueryService).getRateWithFallback(pair);
		verify(rate).convert(amount, ExchangeRate.ConversionDirection.BUY_BASE);
		verifyNoInteractions(conversionRepository, eventPublisher);
		assertThat(meterRegistry.counter("fx.quote.served", "pair", pair.getSymbol()).count())
				.isEqualTo(1.0);
	}

	@Test
	@DisplayName("convert should validate ConvertCommand")
	void testConvertCommandValidation() {
		// Given - invalid command (null paymentId)
		assertThatThrownBy(() -> new ConvertCurrencyUseCase.ConvertCommand(
				null, "ACC-001", "user@bank.com",
				CurrencyPair.of(Currency.EUR, Currency.USD),
				BigDecimal.valueOf(1000), ExchangeRate.ConversionDirection.BUY_BASE
		))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("paymentId required");
	}

	@Test
	@DisplayName("quote should throw RateUnavailableException when rate not found")
	void testQuoteRateUnavailable() {
		// Given
		CurrencyPair pair = CurrencyPair.of(Currency.EUR, Currency.USD);
		when(rateQueryService.getRateWithFallback(pair))
				.thenThrow(new FxRateQueryService.RateUnavailableException("No rates"));

		// When/Then
		assertThatThrownBy(() -> service.quote(pair, BigDecimal.valueOf(1000),
				ExchangeRate.ConversionDirection.BUY_BASE))
				.isInstanceOf(FxRateQueryService.RateUnavailableException.class);

		verifyNoInteractions(conversionRepository, eventPublisher);
	}

	@Test
	@DisplayName("metrics should track successful conversions")
	void testSuccessMetrics() {
		// Given
		ConvertCurrencyUseCase.ConvertCommand command = new ConvertCurrencyUseCase.ConvertCommand(
				"PAY-001", "ACC-001", "user@bank.com",
				CurrencyPair.of(Currency.EUR, Currency.USD),
				BigDecimal.valueOf(1000), ExchangeRate.ConversionDirection.BUY_BASE
		);

		ExchangeRate rate = mock(ExchangeRate.class);
		FxConversion conversion = mock(FxConversion.class);

		when(rateQueryService.getRateWithFallback(command.pair())).thenReturn(rate);
		when(conversionRepository.save(any(FxConversion.class))).thenReturn(conversion);
		when(conversion.pullDomainEvents()).thenReturn(List.of());

		// When
		service.convert(command);

		// Then
		assertThat(meterRegistry.counter("fx.conversion.success", "pair", command.pair().getSymbol()).count())
				.isEqualTo(1.0);
	}
}

