package com.matcodem.fincore.account.domain.port.out;

import com.matcodem.fincore.account.domain.model.IBAN;

public interface IBANGenerator {
	IBAN generate();
}
