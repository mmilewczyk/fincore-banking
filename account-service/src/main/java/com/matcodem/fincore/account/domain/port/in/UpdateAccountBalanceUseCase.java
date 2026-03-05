package com.matcodem.fincore.account.domain.port.in;

import com.matcodem.fincore.account.domain.model.AccountId;
import com.matcodem.fincore.account.domain.model.Money;

public interface UpdateAccountBalanceUseCase {

	void credit(CreditCommand command);

	void debit(DebitCommand command);

	record CreditCommand(AccountId accountId, Money amount, String reference) {
		public CreditCommand {
			if (accountId == null) throw new IllegalArgumentException("AccountId required");
			if (amount == null) throw new IllegalArgumentException("Amount required");
			if (reference == null || reference.isBlank()) throw new IllegalArgumentException("Reference required");
		}
	}

	record DebitCommand(AccountId accountId, Money amount, String reference) {
		public DebitCommand {
			if (accountId == null) throw new IllegalArgumentException("AccountId required");
			if (amount == null) throw new IllegalArgumentException("Amount required");
			if (reference == null || reference.isBlank()) throw new IllegalArgumentException("Reference required");
		}
	}
}