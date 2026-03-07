package com.matcodem.fincore.payment.domain.port.in;

import java.util.List;

import com.matcodem.fincore.payment.domain.model.Payment;
import com.matcodem.fincore.payment.domain.model.PaymentId;

public interface GetPaymentUseCase {

	Payment getPayment(PaymentId paymentId);

	List<Payment> getPaymentsByAccount(String accountId);
}