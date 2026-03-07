package com.matcodem.fincore.payment.adapter.out.persistence;

import org.springframework.stereotype.Component;

import com.matcodem.fincore.payment.domain.model.Currency;
import com.matcodem.fincore.payment.domain.model.IdempotencyKey;
import com.matcodem.fincore.payment.domain.model.Money;
import com.matcodem.fincore.payment.domain.model.Payment;
import com.matcodem.fincore.payment.domain.model.PaymentId;
import com.matcodem.fincore.payment.domain.model.PaymentStatus;
import com.matcodem.fincore.payment.domain.model.PaymentType;
import com.matcodem.fincore.payment.infrastructure.persistence.entity.PaymentJpaEntity;

/**
 * Mapper between Payment aggregate and PaymentJpaEntity.
 * <p>
 * Extracted from PaymentRepositoryAdapter to follow Single Responsibility —
 * the adapter's job is orchestration (find/save/cache), the mapper's job is
 * object graph translation.
 * <p>
 * Why not use MapStruct here?
 * Payment is a DDD aggregate with private constructors and factory methods.
 * MapStruct requires setters or all-args constructors. Using Payment.reconstitute()
 * explicitly is intentional — it communicates that we're restoring state from
 * persistence, not creating a new domain object.
 */
@Component
public class PaymentMapper {

	public PaymentJpaEntity toEntity(Payment p) {
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
		e.setVersion(p.getVersion());
		return e;
	}

	public Payment toDomain(PaymentJpaEntity e) {
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
