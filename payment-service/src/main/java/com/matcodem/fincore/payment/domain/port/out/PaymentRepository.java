package com.matcodem.fincore.payment.domain.port.out;

import java.util.List;
import java.util.Optional;

import com.matcodem.fincore.payment.domain.model.IdempotencyKey;
import com.matcodem.fincore.payment.domain.model.Payment;
import com.matcodem.fincore.payment.domain.model.PaymentId;

public interface PaymentRepository {

	Payment save(Payment payment);

	Optional<Payment> findById(PaymentId paymentId);

	Optional<Payment> findByIdString(String paymentId);

	Optional<Payment> findByIdempotencyKey(IdempotencyKey key);

	List<Payment> findBySourceAccountId(String accountId);

	List<Payment> findByTargetAccountId(String accountId);
}
