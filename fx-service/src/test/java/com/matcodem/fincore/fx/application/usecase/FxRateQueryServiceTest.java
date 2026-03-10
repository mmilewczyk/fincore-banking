package com.matcodem.fincore.fx.application.usecase;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.matcodem.fincore.fx.domain.model.Currency;
import com.matcodem.fincore.fx.domain.model.CurrencyPair;
import com.matcodem.fincore.fx.domain.model.ExchangeRate;
import com.matcodem.fincore.fx.domain.port.out.ExchangeRateRepository;
import com.matcodem.fincore.fx.domain.service.RateRefreshService;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Unit tests for FxRateQueryService.
 * Tests rate lookup strategy: cache -> DB -> providers
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FxRateQueryService")
class FxRateQueryServiceTest {

	@Mock
	private ExchangeRateRepository rateRepository;

	@Mock
	private RateRefreshService rateRefreshService;

	private MeterRegistry meterRegistry;
	private FxRateQueryService service;

	@BeforeEach
	void setUp() {
		meterRegistry = new SimpleMeterRegistry();
		service = new FxRateQueryService(rateRepository, rateRefreshService, meterRegistry);
	}

	@Test
	@DisplayName("getRate should return active rate from cache/DB without fallback")
	void testGetRateSuccess() {
		// Given
		CurrencyPair pair = CurrencyPair.of(Currency.EUR, Currency.USD);
		ExchangeRate rate = mock(ExchangeRate.class);
		when(rate.isActive()).thenReturn(true);
		when(rateRepository.findActiveByPair(pair)).thenReturn(Optional.of(rate));

		// When
		ExchangeRate result = service.getRate(pair);

		// Then
		assertThat(result).isEqualTo(rate);
		verify(rateRepository).findActiveByPair(pair);
		verifyNoInteractions(rateRefreshService);
	}

	@Test
	@DisplayName("getRate should throw RateUnavailableException when no active rate exists")
	void testGetRateNotFound() {
		// Given
		CurrencyPair pair = CurrencyPair.of(Currency.EUR, Currency.USD);
		when(rateRepository.findActiveByPair(pair)).thenReturn(Optional.empty());

		// When/Then
		assertThatThrownBy(() -> service.getRate(pair))
				.isInstanceOf(FxRateQueryService.RateUnavailableException.class);

		verify(rateRepository).findActiveByPair(pair);
		verifyNoInteractions(rateRefreshService);
	}

	@Test
	@DisplayName("getRateWithFallback should use cache hit without calling providers")
	void testGetRateWithFallbackCacheHit() {
		// Given
		CurrencyPair pair = CurrencyPair.of(Currency.EUR, Currency.USD);
		ExchangeRate rate = mock(ExchangeRate.class);
		when(rate.isActive()).thenReturn(true);
		when(rateRepository.findActiveByPair(pair)).thenReturn(Optional.of(rate));

		// When
		ExchangeRate result = service.getRateWithFallback(pair);

		// Then
		assertThat(result).isEqualTo(rate);
		verifyNoInteractions(rateRefreshService);
		assertThat(meterRegistry.counter("fx.rate.cache.hit", "pair", pair.getSymbol()).count())
				.isEqualTo(1.0);
	}

	@Test
	@DisplayName("getRateWithFallback should trigger live fetch on cache miss")
	void testGetRateWithFallbackCacheMiss() {
		// Given
		CurrencyPair pair = CurrencyPair.of(Currency.EUR, Currency.USD);
		ExchangeRate rate = mock(ExchangeRate.class);
		when(rateRepository.findActiveByPair(pair)).thenReturn(Optional.empty());
		when(rateRefreshService.refreshRate(pair)).thenReturn(Optional.of(rate));

		// When
		ExchangeRate result = service.getRateWithFallback(pair);

		// Then
		assertThat(result).isEqualTo(rate);
		verify(rateRepository).findActiveByPair(pair);
		verify(rateRefreshService).refreshRate(pair);
		assertThat(meterRegistry.counter("fx.rate.cache.miss", "pair", pair.getSymbol()).count())
				.isEqualTo(1.0);
	}

	@Test
	@DisplayName("getRateWithFallback should throw exception when all providers fail")
	void testGetRateWithFallbackProviderFail() {
		// Given
		CurrencyPair pair = CurrencyPair.of(Currency.EUR, Currency.USD);
		when(rateRepository.findActiveByPair(pair)).thenReturn(Optional.empty());
		when(rateRefreshService.refreshRate(pair)).thenReturn(Optional.empty());

		// When/Then
		assertThatThrownBy(() -> service.getRateWithFallback(pair))
				.isInstanceOf(FxRateQueryService.RateUnavailableException.class);

		verify(rateRepository).findActiveByPair(pair);
		verify(rateRefreshService).refreshRate(pair);
	}

	@Test
	@DisplayName("getRate should return inactive rate as unavailable")
	void testGetRateInactiveRate() {
		// Given
		CurrencyPair pair = CurrencyPair.of(Currency.EUR, Currency.USD);
		ExchangeRate inactiveRate = mock(ExchangeRate.class);
		when(inactiveRate.isActive()).thenReturn(false);
		when(rateRepository.findActiveByPair(pair)).thenReturn(Optional.of(inactiveRate));

		// When/Then
		assertThatThrownBy(() -> service.getRate(pair))
				.isInstanceOf(FxRateQueryService.RateUnavailableException.class);
	}

	@Test
	@DisplayName("metrics should track cache hits and misses")
	void testMetricsTracking() {
		// Given
		CurrencyPair pair = CurrencyPair.of(Currency.EUR, Currency.USD);
		ExchangeRate rate = mock(ExchangeRate.class);
		when(rate.isActive()).thenReturn(true);
		when(rateRepository.findActiveByPair(pair)).thenReturn(Optional.of(rate));

		// When - call twice, first should be cache hit, second should also be hit
		service.getRateWithFallback(pair);
		service.getRateWithFallback(pair);

		// Then
		assertThat(meterRegistry.counter("fx.rate.cache.hit", "pair", pair.getSymbol()).count())
				.isEqualTo(2.0);
	}
}

