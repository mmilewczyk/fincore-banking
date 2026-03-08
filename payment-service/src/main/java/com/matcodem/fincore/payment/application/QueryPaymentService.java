package com.matcodem.fincore.payment.application;

import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.matcodem.fincore.payment.domain.model.Payment;
import com.matcodem.fincore.payment.domain.model.PaymentId;
import com.matcodem.fincore.payment.domain.port.in.GetPaymentUseCase;
import com.matcodem.fincore.payment.domain.port.out.PaymentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Read-only query service - CQRS query side for payments.
 * <p>
 * All methods are @Transactional(readOnly=true):
 * - Signals intent to Hibernate (no dirty checking, no flush)
 * - Some DB drivers route to read replicas on readOnly=true
 * - Prevents accidental writes from this service
 * <p>
 * getPaymentsByAccount merges sent + received and sorts by recency.
 * If this becomes a performance bottleneck (large accounts), replace with
 * a dedicated CQRS read model (e.g. materialized view or separate projection table).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueryPaymentService implements GetPaymentUseCase {

	private final PaymentRepository paymentRepository;

	@Override
	@Transactional(readOnly = true)
	public Payment getPayment(PaymentId paymentId) {
		return paymentRepository.findById(paymentId)
				.orElseThrow(() -> new NoSuchElementException("Payment not found: " + paymentId));
	}

	@Override
	@Transactional(readOnly = true)
	public List<Payment> getPaymentsByAccount(String accountId) {
		return Stream.concat(
						paymentRepository.findBySourceAccountId(accountId).stream(),
						paymentRepository.findByTargetAccountId(accountId).stream()
				)
				.sorted(Comparator.comparing(Payment::getCreatedAt).reversed())
				.toList();
	}
}