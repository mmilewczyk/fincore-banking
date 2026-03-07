package com.matcodem.fincore.fx.domain.port.in;

import java.math.BigDecimal;

import com.matcodem.fincore.fx.domain.model.CurrencyPair;
import com.matcodem.fincore.fx.domain.model.ExchangeRate;
import com.matcodem.fincore.fx.domain.model.FxConversion;

public interface ConvertCurrencyUseCase {

	FxConversion convert(ConvertCommand command);

	/**
	 * Quote the rate and fee without executing — for UI preview
	 */
	ExchangeRate.ConversionResult quote(CurrencyPair pair, BigDecimal amount,
	                                    ExchangeRate.ConversionDirection direction);

	record ConvertCommand(
			String paymentId,
			String accountId,
			String requestedBy,
			CurrencyPair pair,
			BigDecimal sourceAmount,
			ExchangeRate.ConversionDirection direction
	) {
		public ConvertCommand {
			if (paymentId == null || paymentId.isBlank()) throw new IllegalArgumentException("paymentId required");
			if (accountId == null || accountId.isBlank()) throw new IllegalArgumentException("accountId required");
			if (requestedBy == null || requestedBy.isBlank())
				throw new IllegalArgumentException("requestedBy required");
			if (pair == null) throw new IllegalArgumentException("pair required");
			if (sourceAmount == null || sourceAmount.compareTo(BigDecimal.ZERO) <= 0)
				throw new IllegalArgumentException("sourceAmount must be positive");
			if (direction == null) throw new IllegalArgumentException("direction required");
		}
	}
}
