package com.matcodem.fincore.account.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

import com.matcodem.fincore.account.domain.model.Currency;
import com.matcodem.fincore.account.domain.model.CurrencyMismatchException;
import com.matcodem.fincore.account.domain.model.InsufficientFundsException;
import com.matcodem.fincore.account.domain.model.Money;

@DisplayName("Money Value Object")
class MoneyTest {

	@Test
	@DisplayName("should add two amounts of same currency")
	void shouldAddSameCurrency() {
		Money a = Money.of("100.00", Currency.PLN);
		Money b = Money.of("50.00", Currency.PLN);

		assertThat(a.add(b)).isEqualTo(Money.of("150.00", Currency.PLN));
	}

	@Test
	@DisplayName("should subtract two amounts of same currency")
	void shouldSubtractSameCurrency() {
		Money a = Money.of("100.00", Currency.PLN);
		Money b = Money.of("30.00", Currency.PLN);

		assertThat(a.subtract(b)).isEqualTo(Money.of("70.00", Currency.PLN));
	}

	@Test
	@DisplayName("should throw on subtract that results in negative")
	void shouldThrowOnNegativeSubtract() {
		Money a = Money.of("10.00", Currency.PLN);
		Money b = Money.of("20.00", Currency.PLN);

		assertThatThrownBy(() -> a.subtract(b))
				.isInstanceOf(InsufficientFundsException.class);
	}

	@Test
	@DisplayName("should throw on adding different currencies")
	void shouldThrowOnDifferentCurrencies() {
		Money pln = Money.of("100.00", Currency.PLN);
		Money eur = Money.of("100.00", Currency.EUR);

		assertThatThrownBy(() -> pln.add(eur))
				.isInstanceOf(CurrencyMismatchException.class);
	}

	@Test
	@DisplayName("should reject negative amount")
	void shouldRejectNegativeAmount() {
		assertThatThrownBy(() -> Money.of(new BigDecimal("-1.00"), Currency.PLN))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("should format correctly")
	void shouldFormatCorrectly() {
		Money money = Money.of("1234.50", Currency.PLN);
		assertThat(money.toString()).isEqualTo("1234.50 PLN");
	}

	@Test
	@DisplayName("should be equal for same amount and currency")
	void shouldBeEqual() {
		Money a = Money.of("100.00", Currency.EUR);
		Money b = Money.of("100.00", Currency.EUR);

		assertThat(a).isEqualTo(b);
		assertThat(a.hashCode()).isEqualTo(b.hashCode());
	}
}
