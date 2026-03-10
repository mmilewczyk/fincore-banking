package com.matcodem.fincore.account.domain.port.in;

import java.util.List;

import org.springframework.transaction.annotation.Transactional;

import com.matcodem.fincore.account.domain.model.Account;
import com.matcodem.fincore.account.domain.model.AccountId;
import com.matcodem.fincore.account.domain.port.out.AuditLogRepository;

public interface GetAccountUseCase {

	Account getAccount(AccountId accountId);

	List<Account> getAccountsByOwner(String ownerId);

	@Transactional(readOnly = true)
	List<AuditLogRepository.AuditEntry> getAuditLog(AccountId accountId);
}
