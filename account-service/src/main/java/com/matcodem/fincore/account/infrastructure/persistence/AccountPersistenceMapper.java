package com.matcodem.fincore.account.infrastructure.persistence;

import org.springframework.stereotype.Component;

import com.matcodem.fincore.account.domain.model.Account;
import com.matcodem.fincore.account.domain.model.AccountId;
import com.matcodem.fincore.account.domain.model.AccountStatus;
import com.matcodem.fincore.account.domain.model.Currency;
import com.matcodem.fincore.account.domain.model.IBAN;
import com.matcodem.fincore.account.domain.model.Money;
import com.matcodem.fincore.account.infrastructure.persistence.entity.AccountJpaEntity;

@Component
public class AccountPersistenceMapper {

	public Account toDomain(AccountJpaEntity entity) {
		return Account.reconstitute(
				AccountId.of(entity.getId()),
				entity.getOwnerId(),
				IBAN.of(entity.getIban()),
				Currency.fromCode(entity.getCurrency()),
				Money.of(entity.getBalance(), Currency.fromCode(entity.getCurrency())),
				toStatus(entity.getStatus()),
				entity.getCreatedAt(),
				entity.getUpdatedAt(),
				entity.getVersion()
		);
	}

	public AccountJpaEntity toEntity(Account account) {
		AccountJpaEntity entity = new AccountJpaEntity();
		entity.setId(account.getId().value());
		entity.setOwnerId(account.getOwnerId());
		entity.setIban(account.getIban().getValue());
		entity.setCurrency(account.getCurrency().getCode());
		entity.setBalance(account.getBalance().getAmount());
		entity.setStatus(toJpaStatus(account.getStatus()));
		entity.setCreatedAt(account.getCreatedAt());
		entity.setUpdatedAt(account.getUpdatedAt());
		return entity;
	}

	private AccountStatus toStatus(AccountJpaEntity.AccountStatusJpa jpaStatus) {
		return switch (jpaStatus) {
			case ACTIVE -> AccountStatus.ACTIVE;
			case FROZEN -> AccountStatus.FROZEN;
			case CLOSED -> AccountStatus.CLOSED;
		};
	}

	private AccountJpaEntity.AccountStatusJpa toJpaStatus(AccountStatus status) {
		return switch (status) {
			case ACTIVE -> AccountJpaEntity.AccountStatusJpa.ACTIVE;
			case FROZEN -> AccountJpaEntity.AccountStatusJpa.FROZEN;
			case CLOSED -> AccountJpaEntity.AccountStatusJpa.CLOSED;
		};
	}
}