package com.matcodem.fincore.account.domain.port.in;

import com.matcodem.fincore.account.domain.model.AccountId;

public interface FreezeAccountUseCase {

	void freeze(FreezeCommand command);

	void unfreeze(AccountId accountId);

	record FreezeCommand(AccountId accountId, String reason) {
		public FreezeCommand {
			if (accountId == null) throw new IllegalArgumentException("AccountId required");
			if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Reason required");
		}
	}
}