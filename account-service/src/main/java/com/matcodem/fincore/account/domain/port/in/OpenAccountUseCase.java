package com.matcodem.fincore.account.domain.port.in;

import com.matcodem.fincore.account.domain.model.Account;
import com.matcodem.fincore.account.domain.model.Currency;

public interface OpenAccountUseCase {

	Account openAccount(OpenAccountCommand command);

	record OpenAccountCommand(
			String ownerId,
			Currency currency,
			String email,
			String phoneNumber   // nullable
	) {
		public OpenAccountCommand {
			if (ownerId == null || ownerId.isBlank()) throw new IllegalArgumentException("OwnerId required");
			if (currency == null) throw new IllegalArgumentException("Currency required");
			if (email == null || email.isBlank()) throw new IllegalArgumentException("Email required");
		}
	}
}