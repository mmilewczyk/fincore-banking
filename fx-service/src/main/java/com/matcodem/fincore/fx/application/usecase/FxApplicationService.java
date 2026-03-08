package com.matcodem.fincore.fx.application.usecase;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.matcodem.fincore.fx.domain.model.CurrencyPair;
import com.matcodem.fincore.fx.domain.model.ExchangeRate;
import com.matcodem.fincore.fx.domain.model.FxConversion;
import com.matcodem.fincore.fx.domain.port.in.ConvertCurrencyUseCase;
import com.matcodem.fincore.fx.domain.port.in.GetExchangeRateUseCase;
import com.matcodem.fincore.fx.domain.port.out.ExchangeRateRepository;
import com.matcodem.fincore.fx.domain.port.out.FxConversionRepository;
import com.matcodem.fincore.fx.domain.port.out.FxEventPublisher;
import com.matcodem.fincore.fx.domain.service.RateRefreshService;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * FX Application Service.
 * <p>
 * Orchestration:
 * <p>
 * GetRate:
 * 1. Check Redis cache first (O(1), < 1ms)
 * 2. If cache miss -> check DB for active rate
 * 3. If DB miss or stale -> trigger live fetch via RateRefreshService (provider fallback chain)
 * 4. Return rate or throw RateUnavailableException
 * <p>
 * Convert:
 * 1. Get active rate (above flow)
 * 2. Delegate conversion math to ExchangeRate.convert() (pure domain logic)
 * 3. Persist FxConversion as immutable audit record
 * 4. Publish FxConversionExecutedEvent -> Payment Service can proceed
 * <p>
 * On stale/missing rate:
 * - Persist FxConversion.failed() with reason
 * - Publish FxConversionFailedEvent -> Payment Service fails the payment
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FxApplicationService implements GetExchangeRateUseCase, ConvertCurrencyUseCase {

	private final ExchangeRateRepository rateRepository;
	private final FxConversionRepository conversionRepository;
	private final FxEventPublisher eventPublisher;
	private final RateRefreshService rateRefreshService;
	private final MeterRegistry meterRegistry;

	@Override
	@Timed(value = "fx.rate.get")
	public ExchangeRate getRate(CurrencyPair pair) {
		return rateRepository.findActiveByPair(pair)
				.filter(ExchangeRate::isActive)
				.orElseThrow(() -> new RateUnavailableException(
						"No active rate available for " + pair + ". Try getRateWithFallback()."
				));
	}

	/**
	 * Attempts live refresh if cached rate is missing or stale.
	 * Used by the convert flow where we need the freshest possible rate.
	 */
	@Override
	@Timed(value = "fx.rate.get.with.fallback")
	public ExchangeRate getRateWithFallback(CurrencyPair pair) {
		var cached = rateRepository.findActiveByPair(pair);

		if (cached.isPresent() && cached.get().isActive()) {
			meterRegistry.counter("fx.rate.cache.hit", "pair", pair.getSymbol()).increment();
			return cached.get();
		}

		meterRegistry.counter("fx.rate.cache.miss", "pair", pair.getSymbol()).increment();
		log.info("Rate cache miss for {} - fetching from provider", pair);

		return rateRefreshService.refreshRate(pair)
				.orElseThrow(() -> new RateUnavailableException(
						"All rate providers failed for " + pair + " - conversion rejected"
				));
	}

	@Override
	@Transactional
	@Timed(value = "fx.conversion.duration")
	public FxConversion convert(ConvertCommand command) {
		log.info("FX conversion requested: {} {} for payment {}, account {}",
				command.sourceAmount(), command.pair(), command.paymentId(), command.accountId());

		try {
			ExchangeRate rate = getRateWithFallback(command.pair());

			FxConversion conversion = FxConversion.execute(
					command.paymentId(), command.accountId(), command.requestedBy(),
					rate, command.sourceAmount(), command.direction()
			);

			FxConversion saved = conversionRepository.save(conversion);
			eventPublisher.publishAll(saved.pullDomainEvents());

			meterRegistry.counter("fx.conversion.success",
					"pair", command.pair().getSymbol()).increment();

			log.info("FX conversion executed: {} {} -> {} {} (rate: {}, fee: {})",
					command.sourceAmount(), command.pair().getBase(),
					saved.getConvertedAmount(), command.pair().getQuote(),
					saved.getAppliedRate(), saved.getFee());

			return saved;

		} catch (ExchangeRate.StaleRateException | RateUnavailableException ex) {
			log.error("FX conversion failed for payment {} - {}", command.paymentId(), ex.getMessage());

			FxConversion failed = FxConversion.failed(
					command.paymentId(), command.accountId(), command.requestedBy(),
					command.pair(), command.sourceAmount(), ex.getMessage()
			);
			FxConversion savedFailed = conversionRepository.save(failed);
			eventPublisher.publishAll(savedFailed.pullDomainEvents());

			meterRegistry.counter("fx.conversion.failed",
					"pair", command.pair().getSymbol(),
					"reason", ex.getClass().getSimpleName()).increment();

			throw ex;
		}
	}

	@Override
	@Timed(value = "fx.quote.duration")
	public ExchangeRate.ConversionResult quote(CurrencyPair pair, BigDecimal amount,
	                                           ExchangeRate.ConversionDirection direction) {
		ExchangeRate rate = getRateWithFallback(pair);
		meterRegistry.counter("fx.quote.served", "pair", pair.getSymbol()).increment();
		return rate.convert(amount, direction);
	}

	public static class RateUnavailableException extends RuntimeException {
		public RateUnavailableException(String message) {
			super(message);
		}
	}
}
