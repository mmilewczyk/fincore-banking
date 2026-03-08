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
		// FX fields - null for non-FX payments
		if (p.getConvertedAmount() != null) {
			e.setConvertedAmount(p.getConvertedAmount().getAmount());
			e.setConvertedCurrency(p.getConvertedAmount().getCurrency().getCode());
		}
		e.setFxConversionId(p.getFxConversionId());
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
				e.getVersion(),
				// Reconstitute FX result - null for non-FX payments
				e.getConvertedAmount() != null
						? Money.of(e.getConvertedAmount(), Currency.fromCode(e.getConvertedCurrency()))
						: null,
				e.getFxConversionId()
		);
	}
}