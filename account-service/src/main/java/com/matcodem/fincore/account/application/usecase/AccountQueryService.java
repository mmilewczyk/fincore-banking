package com.matcodem.fincore.account.application.usecase;

import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.matcodem.fincore.account.domain.model.Account;
import com.matcodem.fincore.account.domain.model.AccountId;
import com.matcodem.fincore.account.domain.port.in.GetAccountUseCase;
import com.matcodem.fincore.account.domain.port.out.AccountRepository;
import com.matcodem.fincore.account.domain.port.out.AuditLogRepository;
import com.matcodem.fincore.account.domain.port.out.AuditLogRepository.AuditEntry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountQueryService implements GetAccountUseCase {

	private final AccountRepository accountRepository;
	private final AuditLogRepository auditLogRepository;

	@Override
	@Transactional(readOnly = true)
	public Account getAccount(AccountId accountId) {
		return accountRepository.findById(accountId)
				.orElseThrow(() -> new NoSuchElementException("Account not found: " + accountId));
	}

	@Override
	@Transactional(readOnly = true)
	public List<Account> getAccountsByOwner(String ownerId) {
		return accountRepository.findByOwnerId(ownerId);
	}

	@Override
	@Transactional(readOnly = true)
	public List<AuditEntry> getAuditLog(AccountId accountId) {
		if (!accountRepository.existsById(accountId)) {
			throw new NoSuchElementException("Account not found: " + accountId);
		}
		return auditLogRepository.findByAccountId(accountId);
	}
}