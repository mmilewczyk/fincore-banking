package com.matcodem.fincore.account.application.usecase;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Consumer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.matcodem.fincore.account.domain.event.DomainEvent;
import com.matcodem.fincore.account.domain.model.Account;
import com.matcodem.fincore.account.domain.model.AccountId;
import com.matcodem.fincore.account.domain.port.in.FreezeAccountUseCase;
import com.matcodem.fincore.account.domain.port.in.UpdateAccountBalanceUseCase;
import com.matcodem.fincore.account.domain.port.out.AccountRepository;
import com.matcodem.fincore.account.domain.port.out.AuditLogRepository;
import com.matcodem.fincore.account.domain.port.out.DomainEventPublisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountOperationsService implements UpdateAccountBalanceUseCase, FreezeAccountUseCase {

	private final AccountRepository accountRepository;
	private final DomainEventPublisher eventPublisher;
	private final AuditLogRepository auditLogRepository;

	@Override
	@Transactional
	public void credit(CreditCommand command) {
		log.info("Credit: accountId={}, amount={}", command.accountId(), command.amount());
		executeOnAccount(
				command.accountId(),
				account -> account.credit(command.amount(), command.reference()),
				command.reference()
		);
	}

	@Override
	@Transactional
	public void debit(DebitCommand command) {
		log.info("Debit: accountId={}, amount={}", command.accountId(), command.amount());
		executeOnAccount(
				command.accountId(),
				account -> account.debit(command.amount(), command.reference()),
				command.reference()
		);
	}

	@Override
	@Transactional
	public void freeze(FreezeCommand command) {
		log.warn("Freeze: accountId={}, reason={}", command.accountId(), command.reason());
		executeOnAccount(
				command.accountId(),
				account -> account.freeze(command.reason()),
				"system"
		);
	}

	@Override
	@Transactional
	public void unfreeze(AccountId accountId) {
		log.info("Unfreeze: accountId={}", accountId);
		executeOnAccount(
				accountId,
				Account::unfreeze,
				"system"
		);
	}

	/**
	 * Unified execution template for all account mutation operations.
	 *
	 * @param accountId   the target account
	 * @param operation   domain operation to apply — defined in Account aggregate,
	 *                    throws domain exceptions on invariant violations
	 * @param performedBy audit attribution (userId from JWT, or "system" for internal ops)
	 */
	private void executeOnAccount(AccountId accountId,
	                              Consumer<Account> operation,
	                              String performedBy) {
		Account account = accountRepository.findById(accountId)
				.orElseThrow(() -> new NoSuchElementException("Account not found: " + accountId));

		operation.accept(account);

		accountRepository.save(account);

		publishAndAudit(account, performedBy);
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