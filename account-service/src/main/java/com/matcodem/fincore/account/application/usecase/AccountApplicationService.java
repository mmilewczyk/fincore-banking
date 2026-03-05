package com.matcodem.fincore.account.application.usecase;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.matcodem.fincore.account.domain.event.DomainEvent;
import com.matcodem.fincore.account.domain.model.Account;
import com.matcodem.fincore.account.domain.model.AccountId;
import com.matcodem.fincore.account.domain.model.IBAN;
import com.matcodem.fincore.account.domain.port.in.FreezeAccountUseCase;
import com.matcodem.fincore.account.domain.port.in.GetAccountUseCase;
import com.matcodem.fincore.account.domain.port.in.OpenAccountUseCase;
import com.matcodem.fincore.account.domain.port.in.UpdateAccountBalanceUseCase;
import com.matcodem.fincore.account.domain.port.out.AccountRepository;
import com.matcodem.fincore.account.domain.port.out.AuditLogRepository;
import com.matcodem.fincore.account.domain.port.out.DomainEventPublisher;
import com.matcodem.fincore.account.domain.port.out.IBANGenerator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Application Service — orchestrates domain objects and ports.
 * <p>
 * Rules:
 * - No business logic here — that belongs to the domain
 * - Coordinates: load aggregate -> execute domain operation -> persist -> publish events
 * - Transaction boundary lives here
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountApplicationService implements
		OpenAccountUseCase,
		GetAccountUseCase,
		UpdateAccountBalanceUseCase,
		FreezeAccountUseCase {

	private final AccountRepository accountRepository;
	private final DomainEventPublisher eventPublisher;
	private final AuditLogRepository auditLogRepository;
	private final IBANGenerator ibanGenerator;

	@Override
	@Transactional
	public Account openAccount(OpenAccountUseCase.OpenAccountCommand command) {
		log.info("Opening account for owner: {}, currency: {}", command.ownerId(), command.currency());

		IBAN iban = ibanGenerator.generate();

		// Ensure uniqueness (shouldn't happen but defensive)
		if (accountRepository.existsByIban(iban)) {
			iban = ibanGenerator.generate();
		}

		Account account = Account.open(command.ownerId(), iban, command.currency());
		Account saved = accountRepository.save(account);

		publishAndAudit(saved, "system");

		log.info("Account opened: {}", saved.getId());
		return saved;
	}

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
	@Transactional
	public void credit(CreditCommand command) {
		log.info("Crediting account: {} with {}", command.accountId(), command.amount());

		Account account = loadAccount(command.accountId());
		account.credit(command.amount(), command.reference());
		accountRepository.save(account);

		publishAndAudit(account, command.reference());
	}

	@Override
	@Transactional
	public void debit(DebitCommand command) {
		log.info("Debiting account: {} with {}", command.accountId(), command.amount());

		Account account = loadAccount(command.accountId());
		account.debit(command.amount(), command.reference());
		accountRepository.save(account);

		publishAndAudit(account, command.reference());
	}

	@Override
	@Transactional
	public void freeze(FreezeCommand command) {
		log.warn("Freezing account: {} — reason: {}", command.accountId(), command.reason());

		Account account = loadAccount(command.accountId());
		account.freeze(command.reason());
		accountRepository.save(account);

		auditLogRepository.log(new AuditLogRepository.AuditEntry(
				account.getId(), "account.frozen", "system",
				"Reason: " + command.reason(), Instant.now()
		));
	}

	@Override
	@Transactional
	public void unfreeze(AccountId accountId) {
		log.info("Unfreezing account: {}", accountId);

		Account account = loadAccount(accountId);
		account.unfreeze();
		accountRepository.save(account);

		auditLogRepository.log(new AuditLogRepository.AuditEntry(
				account.getId(), "account.unfrozen", "system", "", Instant.now()
		));
	}

	private Account loadAccount(AccountId accountId) {
		return accountRepository.findById(accountId)
				.orElseThrow(() -> new NoSuchElementException("Account not found: " + accountId));
	}

	private void publishAndAudit(Account account, String performedBy) {
		List<DomainEvent> events = account.pullDomainEvents();
		eventPublisher.publishAll(events);

		events.forEach(event -> auditLogRepository.log(new AuditLogRepository.AuditEntry(
				account.getId(),
				event.eventType(),
				performedBy,
				event.toString(),
				event.occurredAt()
		)));
	}
}