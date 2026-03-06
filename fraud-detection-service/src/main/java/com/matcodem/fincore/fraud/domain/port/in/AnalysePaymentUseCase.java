package com.matcodem.fincore.fraud.domain.port.in;

import com.matcodem.fincore.fraud.domain.model.FraudCase;
import com.matcodem.fincore.fraud.domain.model.PaymentContext;

/**
 * Driving port — analyse a payment for fraud.
 */
public interface AnalysePaymentUseCase {
	FraudCase analyse(PaymentContext context);
}