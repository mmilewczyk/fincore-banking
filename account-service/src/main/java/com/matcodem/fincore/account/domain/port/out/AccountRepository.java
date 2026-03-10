package com.matcodem.fincore.account.domain.port.out;

import java.util.List;
import java.util.Optional;

import com.matcodem.fincore.account.domain.model.Account;
import com.matcodem.fincore.account.domain.model.AccountId;
import com.matcodem.fincore.account.domain.model.IBAN;

public interface AccountRepository {

	Account save(Account account);

	Optional<Account> findById(AccountId accountId);

	Optional<Account> findByIban(IBAN iban);

	List<Account> findByOwnerId(String ownerId);

	boolean existsByIban(IBAN iban);

	boolean existsById(AccountId accountId);
}
