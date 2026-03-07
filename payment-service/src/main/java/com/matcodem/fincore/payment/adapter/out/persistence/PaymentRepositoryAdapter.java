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

	@Override
	public Payment save(Payment payment) {
		var entity = toEntity(payment);
		var saved = paymentJpaRepository.save(entity);
		return toDomain(saved);
	}

	@Override
	public Optional<Payment> findById(PaymentId paymentId) {
		return paymentJpaRepository.findById(paymentId.value()).map(this::toDomain);
	}

	@Override
	public Optional<Payment> findByIdString(String paymentId) {
		return paymentJpaRepository.findById(UUID.fromString(paymentId)).map(this::toDomain);
	}

	@Override
	public Optional<Payment> findByIdempotencyKey(IdempotencyKey key) {
		return paymentJpaRepository.findByIdempotencyKey(key.value()).map(this::toDomain);
	}

	@Override
	public List<Payment> findBySourceAccountId(String accountId) {
		return paymentJpaRepository.findBySourceAccountId(accountId).stream()
				.map(this::toDomain).toList();
	}

	@Override
	public List<Payment> findByTargetAccountId(String accountId) {
		return paymentJpaRepository.findByTargetAccountId(accountId).stream()
				.map(this::toDomain).toList();
	}

	private PaymentJpaEntity toEntity(Payment p) {
		var e = new PaymentJpaEntity();
		e.setId(p.getId().value());
		e.setIdempotencyKey(p.getIdempotencyKey().value());
		e.setSourceAccountId(p.getSourceAccountId());
		e.setTargetAccountId(p.getTargetAccountId());
		e.setAmount(p.getAmount().getAmount());
		e.setCurrency(p.getAmount().getCurrency().getCode());
		e.setType(p.getType().name());
		e.setStatus(p.getStatus().name());
		e.setFailureReason(p.getFailureReason());
		e.setInitiatedBy(p.getInitiatedBy());
		e.setCreatedAt(p.getCreatedAt());
		e.setUpdatedAt(p.getUpdatedAt());
		return e;
	}

	private Payment toDomain(PaymentJpaEntity e) {
		return Payment.reconstitute(
				PaymentId.of(e.getId()),
				IdempotencyKey.of(e.getIdempotencyKey()),
				e.getSourceAccountId(),
				e.getTargetAccountId(),
				Money.of(e.getAmount(), Currency.fromCode(e.getCurrency())),
				PaymentType.valueOf(e.getType()),
				e.getInitiatedBy(),
				PaymentStatus.valueOf(e.getStatus()),
				e.getFailureReason(),
				e.getCreatedAt(),
				e.getUpdatedAt(),
				e.getVersion()
		);
	}
}