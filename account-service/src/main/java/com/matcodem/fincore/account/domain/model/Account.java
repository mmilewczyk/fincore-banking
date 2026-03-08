package com.matcodem.fincore.account.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.matcodem.fincore.account.domain.event.AccountCreatedEvent;
import com.matcodem.fincore.account.domain.event.AccountCreditedEvent;
import com.matcodem.fincore.account.domain.event.AccountDebitedEvent;
import com.matcodem.fincore.account.domain.event.AccountFrozenEvent;
import com.matcodem.fincore.account.domain.event.DomainEvent;

/**
 * Account Aggregate Root.
 * <p>
 * Encapsulates all business rules around a bank account:
 * - Balance management (debit/credit)
 * - Status transitions (ACTIVE -> FROZEN -> CLOSED)
 * - Domain event recording for eventual consistency
 * <p>
 * No JPA annotations here - domain is infrastructure-agnostic.
 */
public class Account {

	private final AccountId id;
	private final String ownerId;       // Reference to user (not an entity here)
	private final IBAN iban;
	private final Currency currency;
	private Money balance;
	private AccountStatus status;
	private final Instant createdAt;
	private Instant updatedAt;
	private long version;               // For optimistic locking

	private final List<DomainEvent> domainEvents = new ArrayList<>();

	private Account(AccountId id, String ownerId, IBAN iban, Currency currency,
	                Money initialBalance, AccountStatus status, Instant createdAt, long version) {
		this.id = id;
		this.ownerId = ownerId;
		this.iban = iban;
		this.currency = currency;
		this.balance = initialBalance;
		this.status = status;
		this.createdAt = createdAt;
		this.updatedAt = createdAt;
		this.version = version;
	}

	/**
	 * Factory method - creates a new account and records domain event.
	 */
	public static Account open(String ownerId, IBAN iban, Currency currency) {
		Objects.requireNonNull(ownerId, "OwnerId cannot be null");
		Objects.requireNonNull(iban, "IBAN cannot be null");
		Objects.requireNonNull(currency, "Currency cannot be null");

		AccountId id = AccountId.generate();
		Instant now = Instant.now();
		Money initialBalance = Money.zero(currency);

		Account account = new Account(id, ownerId, iban, currency, initialBalance,
				AccountStatus.ACTIVE, now, 0L);

		account.recordEvent(new AccountCreatedEvent(id, ownerId, iban, currency, now));
		return account;
	}

	/**
	 * Reconstitution from persistence - no events recorded.
	 */
	public static Account reconstitute(AccountId id, String ownerId, IBAN iban,
	                                   Currency currency, Money balance, AccountStatus status,
	                                   Instant createdAt, Instant updatedAt, long version) {
		Account account = new Account(id, ownerId, iban, currency, balance, status, createdAt, version);
		account.updatedAt = updatedAt;
		return account;
	}

	public void credit(Money amount, String reference) {
		assertActive();
		Objects.requireNonNull(amount, "Amount cannot be null");
		Objects.requireNonNull(reference, "Reference cannot be null");
		assertSameCurrency(amount);

		this.balance = this.balance.add(amount);
		this.updatedAt = Instant.now();
		recordEvent(new AccountCreditedEvent(id, amount, balance, reference, updatedAt));
	}

	public void debit(Money amount, String reference) {
		assertActive();
		Objects.requireNonNull(amount, "Amount cannot be null");
		Objects.requireNonNull(reference, "Reference cannot be null");
		assertSameCurrency(amount);
		assertSufficientFunds(amount);

		this.balance = this.balance.subtract(amount);
		this.updatedAt = Instant.now();
		recordEvent(new AccountDebitedEvent(id, amount, balance, reference, updatedAt));
	}

	public void freeze(String reason) {
		if (status == AccountStatus.CLOSED) {
			throw new IllegalStateException("Cannot freeze a closed account");
		}
		this.status = AccountStatus.FROZEN;
		this.updatedAt = Instant.now();
		recordEvent(new AccountFrozenEvent(id, reason, updatedAt));
	}

	public void unfreeze() {
		if (status != AccountStatus.FROZEN) {
			throw new IllegalStateException("Account is not frozen");
		}
		this.status = AccountStatus.ACTIVE;
		this.updatedAt = Instant.now();
	}

	public void close() {
		if (!balance.isZero()) {
			throw new IllegalStateException("Cannot close account with non-zero balance: " + balance);
		}
		this.status = AccountStatus.CLOSED;
		this.updatedAt = Instant.now();
	}

	private void assertActive() {
		if (status != AccountStatus.ACTIVE) {
			throw new AccountNotActiveException(
					"Account %s is not active (status: %s)".formatted(id, status)
			);
		}
	}

	private void assertSameCurrency(Money amount) {
		if (!amount.getCurrency().equals(this.currency)) {
			throw new CurrencyMismatchException(
					"Account currency %s does not match operation currency %s"
							.formatted(this.currency, amount.getCurrency())
			);
		}
	}

	private void assertSufficientFunds(Money amount) {
		if (!this.balance.isGreaterThanOrEqual(amount)) {
			throw new InsufficientFundsException(
					"Insufficient funds. Balance: %s, requested: %s".formatted(balance, amount)
			);
		}
	}

	private void recordEvent(DomainEvent event) {
		domainEvents.add(event);
	}

	public List<DomainEvent> pullDomainEvents() {
		List<DomainEvent> events = new ArrayList<>(domainEvents);
		domainEvents.clear();
		return Collections.unmodifiableList(events);
	}

	public AccountId getId() {
		return id;
	}

	public String getOwnerId() {
		return ownerId;
	}

	public IBAN getIban() {
		return iban;
	}

	public Currency getCurrency() {
		return currency;
	}

	public Money getBalance() {
		return balance;
	}

	public AccountStatus getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public long getVersion() {
		return version;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof Account account)) return false;
		return Objects.equals(id, account.id);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id);
	}

	@Override
	public String toString() {
		return "Account{id=%s, iban=%s, balance=%s, status=%s}".formatted(id, iban, balance, status);
	}
}
