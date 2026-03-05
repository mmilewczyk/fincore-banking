package com.matcodem.fincore.account.adapter.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.matcodem.fincore.account.domain.model.Account;
import com.matcodem.fincore.account.domain.model.AccountId;
import com.matcodem.fincore.account.domain.model.IBAN;
import com.matcodem.fincore.account.domain.port.out.AccountRepository;
import com.matcodem.fincore.account.infrastructure.persistence.AccountPersistenceMapper;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AccountRepositoryAdapter implements AccountRepository {

	private final com.matcodem.fincore.account.infrastructure.persistence.repository.AccountJpaRepository accountJpaRepository;
	private final AccountPersistenceMapper mapper;

	@Override
	public Account save(Account account) {
		var entity = mapper.toEntity(account);
		var saved = accountJpaRepository.save(entity);
		return mapper.toDomain(saved);
	}

	@Override
	public Optional<Account> findById(AccountId accountId) {
		return accountJpaRepository.findById(accountId.value())
				.map(mapper::toDomain);
	}

	@Override
	public Optional<Account> findByIban(IBAN iban) {
		return accountJpaRepository.findByIban(iban.getValue())
				.map(mapper::toDomain);
	}

	@Override
	public List<Account> findByOwnerId(String ownerId) {
		return accountJpaRepository.findByOwnerId(ownerId).stream()
				.map(mapper::toDomain)
				.toList();
	}

	@Override
	public boolean existsByIban(IBAN iban) {
		return accountJpaRepository.existsByIban(iban.getValue());
	}
}