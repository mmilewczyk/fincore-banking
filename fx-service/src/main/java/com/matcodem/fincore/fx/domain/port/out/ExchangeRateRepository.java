package com.matcodem.fincore.fx.domain.port.out;


import java.util.List;
import java.util.Optional;

import com.matcodem.fincore.fx.domain.model.CurrencyPair;
import com.matcodem.fincore.fx.domain.model.ExchangeRate;
import com.matcodem.fincore.fx.domain.model.ExchangeRateId;

public interface ExchangeRateRepository {
	ExchangeRate save(ExchangeRate rate);

	Optional<ExchangeRate> findActiveByPair(CurrencyPair pair);

	Optional<ExchangeRate> findById(ExchangeRateId id);

	List<ExchangeRate> findAllActive();

	void supersedeAllForPair(CurrencyPair pair, ExchangeRateId newRateId);
}