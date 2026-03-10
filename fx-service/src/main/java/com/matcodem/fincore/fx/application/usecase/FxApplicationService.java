package com.matcodem.fincore.fx.application.usecase;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;

import com.matcodem.fincore.fx.domain.model.CurrencyPair;
import com.matcodem.fincore.fx.domain.model.ExchangeRate;
import com.matcodem.fincore.fx.domain.model.FxConversion;
import com.matcodem.fincore.fx.domain.port.in.ConvertCurrencyUseCase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * FX Application Service - thin facade/orchestrator.
 * <p>
 * Delegates to specialized services:
 * - FxRateQueryService: rate fetching with fallback
 * - FxConversionService: conversion execution and quoting
 * <p>
 * Implements:
 * - GetExchangeRateUseCase (get single rate, get all rates)
 * - ConvertCurrencyUseCase (execute conversions)
 * <p>
 * Responsibilities:
 * - Provide unified interface
 * - Delegate to appropriate specialized service
 * - Minimal orchestration logic
 * <p>
 * Does NOT handle:
 * - Rate fetching (FxRateQueryService)
 * - Conversion execution (FxConversionService)
 * - Domain logic (domain objects)
 * - Event publishing (FxConversionService)
 * <p>
 * Benefits of this split:
 * - Single Responsibility: each service has one reason to change
 * - Testability: each service tested independently
 * - Maintainability: clear separation of concerns
 * - Reusability: services can be used by different facades
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FxApplicationService {

	private final FxRateQueryService rateQueryService;
	private final FxConversionService conversionService;

	public ExchangeRate getRate(CurrencyPair pair) {
		return rateQueryService.getRate(pair);
	}

	public ExchangeRate getRateWithFallback(CurrencyPair pair) {
		return rateQueryService.getRateWithFallback(pair);
	}

	public List<ExchangeRate> getAllActiveRates() {
		return rateQueryService.getAllActiveRates();
	}

	public FxConversion convert(ConvertCurrencyUseCase.ConvertCommand command) {
		return conversionService.convert(command);
	}

	public ExchangeRate.ConversionResult quote(CurrencyPair pair, BigDecimal amount,
	                                           ExchangeRate.ConversionDirection direction) {
		return conversionService.quote(pair, amount, direction);
	}
}
