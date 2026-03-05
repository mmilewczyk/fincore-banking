package com.matcodem.fincore.payment.domain.domain.port.in;

import com.matcodem.fincore.payment.domain.model.PaymentId;

public interface ProcessPaymentUseCase {
	void processPayment(PaymentId paymentId);
}
