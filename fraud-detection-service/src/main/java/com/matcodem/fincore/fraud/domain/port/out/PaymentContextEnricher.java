package com.matcodem.fincore.fraud.domain.port.out;

import com.matcodem.fincore.fraud.domain.model.PaymentContext;

/**
 * Driven port — enriches raw payment data with account + behavioral context.
 * Implementation fetches from Account Service and transaction history.
 */
public interface PaymentContextEnricher {
	PaymentContext enrich(PaymentContext rawContext);
}