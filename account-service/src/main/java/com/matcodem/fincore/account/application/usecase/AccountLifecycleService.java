package com.matcodem.fincore.account.application.usecase;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.matcodem.fincore.account.domain.event.DomainEvent;
import com.matcodem.fincore.account.domain.model.Account;
import com.matcodem.fincore.account.domain.model.IBAN;
import com.matcodem.fincore.account.domain.port.in.OpenAccountUseCase;
import com.matcodem.fincore.account.domain.port.out.AccountRepository;
import com.matcodem.fincore.account.domain.port.out.AuditLogRepository;
import com.matcodem.fincore.account.domain.port.out.DomainEventPublisher;
import com.matcodem.fincore.account.domain.port.out.IBANGenerator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountLifecycleService implements OpenAccountUseCase {

	private final AccountRepository accountRepository;
	private final DomainEventPublisher eventPublisher;
	private final AuditLogRepository auditLogRepository;
	private final IBANGenerator ibanGenerator;

	@Override
	@Transactional
	public Account openAccount(OpenAccountCommand command) {
		log.info("Opening account: ownerId={}, currency={}", command.ownerId(), command.currency());

		IBAN iban = generateUniqueIban();
		Account account = Account.open(command.ownerId(), iban, command.currency(),
				command.email(), command.phoneNumber());

		Account saved = accountRepository.save(account);

		publishEvents(saved);
		auditEvents(saved, "system");

		log.info("Account opened: id={}", saved.getId());
		return saved;
	}

	private IBAN generateUniqueIban() {
		IBAN iban = ibanGenerator.generate();
		// Defensive retry - collision probability is negligible but we guard it explicitly
		if (accountRepository.existsByIban(iban)) {
			log.warn("IBAN collision detected — regenerating");
			iban = ibanGenerator.generate();
		}
		return iban;
	}

	private void publishEvents(Account account) {
		List<DomainEvent> events = account.pullDomainEvents();
		eventPublisher.publishAll(events);
	}

	private void auditEvents(Account account, String performedBy) {
		auditLogRepository.log(new AuditLogRepository.AuditEntry(
				account.getId(),
				"account.created",
				performedBy,
				"iban=%s currency=%s".formatted(account.getIban(), account.getCurrency()),
				account.getCreatedAt()
		));
	}
}