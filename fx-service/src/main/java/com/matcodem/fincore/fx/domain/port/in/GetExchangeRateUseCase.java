package com.matcodem.fincore.fx.domain.port.in;

import java.util.List;

import com.matcodem.fincore.fx.domain.model.CurrencyPair;
import com.matcodem.fincore.fx.domain.model.ExchangeRate;

public interface GetExchangeRateUseCase {
	ExchangeRate getRate(CurrencyPair pair);

	ExchangeRate getRateWithFallback(CurrencyPair pair);

	List<ExchangeRate> getAllActiveRates();
}