package com.matcodem.fincore.account.domain.port.in;

import java.util.List;

import com.matcodem.fincore.account.domain.model.Account;
import com.matcodem.fincore.account.domain.model.AccountId;

public interface GetAccountUseCase {

	Account getAccount(AccountId accountId);

	List<Account> getAccountsByOwner(String ownerId);
}
