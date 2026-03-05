package com.matcodem.fincore.account.adapter.in.web.mapper;

import org.springframework.stereotype.Component;

import com.matcodem.fincore.account.adapter.in.web.dto.AccountResponse;
import com.matcodem.fincore.account.domain.model.Account;

@Component
public class AccountWebMapper {

	public AccountResponse toResponse(Account account) {
		return new AccountResponse(
				account.getId().toString(),
				account.getOwnerId(),
				account.getIban().getFormatted(),
				account.getCurrency(),
				account.getBalance().getAmount(),
				account.getStatus(),
				account.getCreatedAt(),
				account.getUpdatedAt()
		);
	}
}
