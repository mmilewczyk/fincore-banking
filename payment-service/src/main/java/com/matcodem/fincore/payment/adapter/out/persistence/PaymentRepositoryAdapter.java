package com.matcodem.fincore.payment.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.matcodem.fincore.payment.domain.port.out.PaymentRepository;
import com.matcodem.fincore.payment.domain.model.Currency;
import com.matcodem.fincore.payment.domain.model.IdempotencyKey;
import com.matcodem.fincore.payment.domain.model.Money;
import com.matcodem.fincore.payment.domain.model.Payment;
import com.matcodem.fincore.payment.domain.model.PaymentId;
import com.matcodem.fincore.payment.domain.model.PaymentStatus;
import com.matcodem.fincore.payment.domain.model.PaymentType;
import com.matcodem.fincore.payment.infrastructure.persistence.entity.PaymentJpaEntity;
import com.matcodem.fincore.payment.infrastructure.persistence.repository.PaymentJpaRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PaymentRepositoryAdapter implements PaymentRepository {

	private final PaymentJpaRepository paymentJpaRepository;
	private final PaymentMapper paymentMapper;

	@Override
	public Payment save(Payment payment) {
		var entity = paymentMapper.toEntity(payment);
		var saved = paymentJpaRepository.save(entity);
		return paymentMapper.toDomain(saved);
	}

	@Override
	public Optional<Payment> findById(PaymentId paymentId) {
		return paymentJpaRepository.findById(paymentId.value()).map(paymentMapper::toDomain);
	}

	@Override
	public Optional<Payment> findByIdString(String paymentId) {
		return paymentJpaRepository.findById(UUID.fromString(paymentId)).map(paymentMapper::toDomain);
	}

	@Override
	public Optional<Payment> findByIdempotencyKey(IdempotencyKey key) {
		return paymentJpaRepository.findByIdempotencyKey(key.value()).map(paymentMapper::toDomain);
	}

	@Override
	public List<Payment> findBySourceAccountId(String accountId) {
		return paymentJpaRepository.findBySourceAccountId(accountId).stream()
				.map(paymentMapper::toDomain).toList();
	}

	@Override
	public List<Payment> findByTargetAccountId(String accountId) {
		return paymentJpaRepository.findByTargetAccountId(accountId).stream()
				.map(paymentMapper::toDomain).toList();
	}
}