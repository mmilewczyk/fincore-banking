package com.matcodem.fincore.fx.application.usecase;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.matcodem.fincore.fx.domain.model.CurrencyPair;
import com.matcodem.fincore.fx.domain.model.ExchangeRate;
import com.matcodem.fincore.fx.domain.model.FxConversion;
import com.matcodem.fincore.fx.domain.port.in.ConvertCurrencyUseCase;
import com.matcodem.fincore.fx.domain.port.out.FxConversionRepository;
import com.matcodem.fincore.fx.domain.port.out.FxOutboxRepository;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class FxConversionService implements ConvertCurrencyUseCase {

	private static final String AGGREGATE_TYPE = "FxConversion";

	private final FxConversionRepository conversionRepository;
	private final FxOutboxRepository outboxRepository;
	private final FxRateQueryService rateQueryService;
	private final MeterRegistry meterRegistry;

	@Override
	@Transactional
	@Timed(value = "fx.conversion.duration")
	public FxConversion convert(ConvertCommand command) {
		var existing = conversionRepository.findByPaymentId(command.paymentId());
		if (existing.isPresent()) {
			log.info("Idempotent hit - returning existing conversion for paymentId={}",
					command.paymentId());
			meterRegistry.counter("fx.conversion.idempotent.hits").increment();
			return existing.get();
		}

		log.info("FX conversion requested: {} {} -> {} for payment {}, account {}",
				command.sourceAmount(), command.pair().getBase(),
				command.pair().getQuote(),
				command.paymentId(), command.accountId());

		try {
			return executeConversion(command);
		} catch (ExchangeRate.StaleRateException | FxRateQueryService.RateUnavailableException ex) {
			return handleConversionFailure(command, ex);
		}
	}

	private FxConversion executeConversion(ConvertCommand command) {
		ExchangeRate rate = rateQueryService.getRateWithFallback(command.pair());

		FxConversion conversion = FxConversion.execute(
				command.paymentId(), command.accountId(), command.requestedBy(),
				rate, command.sourceAmount(), command.direction()
		);

		// Pull before save - JPA may return a new instance after mapping,
		// which would have an empty events list. Same pattern as payment-service.
		var events = conversion.pullDomainEvents();

		FxConversion saved = conversionRepository.save(conversion);

		outboxRepository.append(events, AGGREGATE_TYPE);

		meterRegistry.counter("fx.conversion.success",
				"pair", command.pair().getSymbol()).increment();

		log.info("FX conversion executed successfully: {} {} -> {} {} (rate: {}, fee: {})",
				command.sourceAmount(), command.pair().getBase(),
				saved.getConvertedAmount(), command.pair().getQuote(),
				saved.getAppliedRate(), saved.getFee());

		return saved;
	}

	private FxConversion handleConversionFailure(ConvertCommand command, RuntimeException ex) {
		log.error("FX conversion failed for payment {} - {}", command.paymentId(), ex.getMessage());

		FxConversion failed = FxConversion.failed(
				command.paymentId(), command.accountId(), command.requestedBy(),
				command.pair(), command.sourceAmount(), ex.getMessage()
		);

		var events = failed.pullDomainEvents();
		conversionRepository.save(failed);

		outboxRepository.append(events, AGGREGATE_TYPE);

		meterRegistry.counter("fx.conversion.failed",
				"pair", command.pair().getSymbol(),
				"reason", ex.getClass().getSimpleName()).increment();
		throw ex;
	}

	@Override
	@Timed(value = "fx.quote.duration")
	public ExchangeRate.ConversionResult quote(CurrencyPair pair, BigDecimal amount,
	                                           ExchangeRate.ConversionDirection direction) {
		ExchangeRate rate = rateQueryService.getRateWithFallback(pair);
		ExchangeRate.ConversionResult result = rate.convert(amount, direction);

		meterRegistry.counter("fx.quote.served", "pair", pair.getSymbol()).increment();

		log.debug("FX quote provided: {} {} -> {} {} (fee: {})",
				amount, pair.getBase(),
				result.convertedAmount(), pair.getQuote(),
				result.fee());

		return result;
	}
}

