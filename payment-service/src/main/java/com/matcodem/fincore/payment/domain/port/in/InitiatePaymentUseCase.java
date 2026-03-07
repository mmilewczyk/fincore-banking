package com.matcodem.fincore.payment.domain.port.in;


import com.matcodem.fincore.payment.domain.model.IdempotencyKey;
import com.matcodem.fincore.payment.domain.model.Money;
import com.matcodem.fincore.payment.domain.model.Payment;
import com.matcodem.fincore.payment.domain.model.PaymentType;

public interface InitiatePaymentUseCase {

	Payment initiatePayment(InitiatePaymentCommand command);

	record InitiatePaymentCommand(
			IdempotencyKey idempotencyKey,
			String sourceAccountId,
			String targetAccountId,
			Money amount,
			PaymentType type,
			String initiatedBy
	) {
		public InitiatePaymentCommand {
			if (idempotencyKey == null) throw new IllegalArgumentException("IdempotencyKey required");
			if (sourceAccountId == null || sourceAccountId.isBlank())
				throw new IllegalArgumentException("Source account required");
			if (targetAccountId == null || targetAccountId.isBlank())
				throw new IllegalArgumentException("Target account required");
			if (amount == null) throw new IllegalArgumentException("Amount required");
			if (type == null) throw new IllegalArgumentException("Type required");
			if (initiatedBy == null || initiatedBy.isBlank())
				throw new IllegalArgumentException("InitiatedBy required");
		}
	}
}