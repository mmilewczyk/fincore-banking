package com.matcodem.fincore.account.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.matcodem.fincore.account.domain.event.AccountCreatedEvent;
import com.matcodem.fincore.account.domain.event.AccountCreditedEvent;
import com.matcodem.fincore.account.domain.event.AccountDebitedEvent;
import com.matcodem.fincore.account.domain.event.DomainEvent;
import com.matcodem.fincore.account.domain.model.Account;
import com.matcodem.fincore.account.domain.model.AccountNotActiveException;
import com.matcodem.fincore.account.domain.model.AccountStatus;
import com.matcodem.fincore.account.domain.model.Currency;
import com.matcodem.fincore.account.domain.model.CurrencyMismatchException;
import com.matcodem.fincore.account.domain.model.IBAN;
import com.matcodem.fincore.account.domain.model.InsufficientFundsException;
import com.matcodem.fincore.account.domain.model.Money;

@DisplayName("Account Aggregate")
class AccountTest {

	private static final String OWNER_ID = "user-123";
	private static final IBAN TEST_IBAN = IBAN.of("PL61109010140000071219812874");
	private static final Currency PLN = Currency.PLN;

	private Account account;

	@BeforeEach
	void setUp() {
		account = Account.open(OWNER_ID, TEST_IBAN, PLN);
		account.pullDomainEvents(); // clear creation event
	}

	@Nested
	@DisplayName("Opening an account")
	class OpenAccount {

		@Test
		@DisplayName("should create account with zero balance")
		void shouldCreateWithZeroBalance() {
			Account newAccount = Account.open(OWNER_ID, TEST_IBAN, PLN);

			assertThat(newAccount.getBalance()).isEqualTo(Money.zero(PLN));
			assertThat(newAccount.getStatus()).isEqualTo(AccountStatus.ACTIVE);
			assertThat(newAccount.getOwnerId()).isEqualTo(OWNER_ID);
		}

		@Test
		@DisplayName("should record AccountCreatedEvent")
		void shouldRecordCreatedEvent() {
			Account newAccount = Account.open(OWNER_ID, TEST_IBAN, PLN);
			List<DomainEvent> events = newAccount.pullDomainEvents();

			assertThat(events).hasSize(1);
			assertThat(events.getFirst()).isInstanceOf(AccountCreatedEvent.class);
		}

		@Test
		@DisplayName("should reject null ownerId")
		void shouldRejectNullOwnerId() {
			assertThatThrownBy(() -> Account.open(null, TEST_IBAN, PLN))
					.isInstanceOf(NullPointerException.class);
		}
	}

	@Nested
	@DisplayName("Crediting")
	class Credit {

		@Test
		@DisplayName("should increase balance")
		void shouldIncreaseBalance() {
			Money amount = Money.of("100.00", PLN);
			account.credit(amount, "REF-001");

			assertThat(account.getBalance()).isEqualTo(amount);
		}

		@Test
		@DisplayName("should record AccountCreditedEvent")
		void shouldRecordEvent() {
			account.credit(Money.of("50.00", PLN), "REF-001");
			var events = account.pullDomainEvents();

			assertThat(events).hasSize(1);
			assertThat(events.getFirst()).isInstanceOf(AccountCreditedEvent.class);
		}

		@Test
		@DisplayName("should reject credit on frozen account")
		void shouldRejectOnFrozenAccount() {
			account.freeze("Test freeze");

			assertThatThrownBy(() -> account.credit(Money.of("100.00", PLN), "REF"))
					.isInstanceOf(AccountNotActiveException.class);
		}

		@Test
		@DisplayName("should reject different currency")
		void shouldRejectDifferentCurrency() {
			assertThatThrownBy(() -> account.credit(Money.of("100.00", Currency.EUR), "REF"))
					.isInstanceOf(CurrencyMismatchException.class);
		}
	}

	@Nested
	@DisplayName("Debiting")
	class Debit {

		@BeforeEach
		void creditAccount() {
			account.credit(Money.of("500.00", PLN), "INITIAL");
			account.pullDomainEvents();
		}

		@Test
		@DisplayName("should decrease balance")
		void shouldDecreaseBalance() {
			account.debit(Money.of("200.00", PLN), "PAYMENT-001");

			assertThat(account.getBalance()).isEqualTo(Money.of("300.00", PLN));
		}

		@Test
		@DisplayName("should record AccountDebitedEvent")
		void shouldRecordEvent() {
			account.debit(Money.of("100.00", PLN), "PAYMENT-001");
			var events = account.pullDomainEvents();

			assertThat(events).hasSize(1);
			assertThat(events.getFirst()).isInstanceOf(AccountDebitedEvent.class);
		}

		@Test
		@DisplayName("should reject debit exceeding balance")
		void shouldRejectInsufficientFunds() {
			assertThatThrownBy(() -> account.debit(Money.of("600.00", PLN), "PAYMENT"))
					.isInstanceOf(InsufficientFundsException.class)
					.hasMessageContaining("Insufficient funds");
		}

		@Test
		@DisplayName("should allow debit of exact balance")
		void shouldAllowExactBalance() {
			assertThatNoException().isThrownBy(
					() -> account.debit(Money.of("500.00", PLN), "EXACT")
			);
			assertThat(account.getBalance()).isEqualTo(Money.zero(PLN));
		}
	}

	@Nested
	@DisplayName("Status transitions")
	class StatusTransitions {

		@Test
		@DisplayName("should freeze active account")
		void shouldFreezeActiveAccount() {
			account.freeze("Suspicious activity");
			assertThat(account.getStatus()).isEqualTo(AccountStatus.FROZEN);
		}

		@Test
		@DisplayName("should unfreeze frozen account")
		void shouldUnfreezeFrozenAccount() {
			account.freeze("Test");
			account.unfreeze();
			assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
		}

		@Test
		@DisplayName("should reject closing account with balance")
		void shouldRejectClosingWithBalance() {
			account.credit(Money.of("100.00", PLN), "REF");

			assertThatThrownBy(() -> account.close())
					.isInstanceOf(IllegalStateException.class)
					.hasMessageContaining("non-zero balance");
		}

		@Test
		@DisplayName("should close account with zero balance")
		void shouldCloseWithZeroBalance() {
			assertThatNoException().isThrownBy(() -> account.close());
			assertThat(account.getStatus()).isEqualTo(AccountStatus.CLOSED);
		}
	}
}
