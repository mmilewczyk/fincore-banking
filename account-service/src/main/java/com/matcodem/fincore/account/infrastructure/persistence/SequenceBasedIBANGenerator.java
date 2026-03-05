package com.matcodem.fincore.account.infrastructure.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.matcodem.fincore.account.domain.model.IBAN;
import com.matcodem.fincore.account.domain.port.out.IBANGenerator;

import lombok.RequiredArgsConstructor;

/**
 * Generates Polish IBANs using a DB sequence for uniqueness.
 * Bank code: 10500099 (fictional)
 */
@Component
@RequiredArgsConstructor
public class SequenceBasedIBANGenerator implements IBANGenerator {

	private final JdbcTemplate jdbcTemplate;

	private static final String BANK_CODE = "10500099";

	@Override
	public IBAN generate() {
		Long seq = jdbcTemplate.queryForObject(
				"SELECT nextval('iban_sequence')", Long.class
		);
		// Pad to 16 digits (bank code 8 + account number 16 = 24 digit BBAN)
		String accountNumber = String.format("%016d", seq);
		return IBAN.generatePolish(BANK_CODE, accountNumber);
	}
}