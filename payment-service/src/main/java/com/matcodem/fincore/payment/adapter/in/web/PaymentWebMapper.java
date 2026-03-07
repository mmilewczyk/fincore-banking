package com.matcodem.fincore.payment.adapter.in.web;

import org.springframework.stereotype.Component;

import com.matcodem.fincore.payment.adapter.in.web.dto.PaymentResponse;
import com.matcodem.fincore.payment.domain.model.Payment;

/**
 * Maps Payment aggregate -> PaymentResponse DTO.
 * <p>
 * Extracted from PaymentController to follow Single Responsibility.
 * Controller stays thin: routing, auth, HTTP semantics only.
 */
@Component
public class PaymentWebMapper {

	public PaymentResponse toResponse(Payment p) {
		return new PaymentResponse(
				p.getId().toString(),
				p.getIdempotencyKey().value(),
				p.getSourceAccountId(),
				p.getTargetAccountId(),
				p.getAmount().getAmount(),
				p.getAmount().getCurrency().getCode(),
				p.getType().name(),
				p.getStatus().name(),
				p.getFailureReason(),
				p.getInitiatedBy(),
				p.getCreatedAt(),
				p.getUpdatedAt()
		);
	}
}