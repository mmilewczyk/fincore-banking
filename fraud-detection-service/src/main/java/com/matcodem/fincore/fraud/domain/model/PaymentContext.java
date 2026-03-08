package com.matcodem.fincore.fraud.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Value Object - immutable snapshot of a payment used for rule evaluation.
 * <p>
 * Passed to every FraudRule. Contains everything a rule might need
 * to make a decision, including enriched context fetched from other services.
 */
public final class PaymentContext {

	// Core payment data
	private final String paymentId;
	private final String idempotencyKey;
	private final String sourceAccountId;
	private final String targetAccountId;
	private final BigDecimal amount;
	private final String currency;
	private final String paymentType;
	private final String initiatedBy;
	private final Instant initiatedAt;

	// Enriched context (fetched asynchronously before evaluation)
	private final AccountContext sourceAccount;
	private final AccountContext targetAccount;
	private final UserBehaviorContext userBehavior;

	private PaymentContext(Builder builder) {
		this.paymentId = Objects.requireNonNull(builder.paymentId);
		this.idempotencyKey = Objects.requireNonNull(builder.idempotencyKey);
		this.sourceAccountId = Objects.requireNonNull(builder.sourceAccountId);
		this.targetAccountId = Objects.requireNonNull(builder.targetAccountId);
		this.amount = Objects.requireNonNull(builder.amount);
		this.currency = Objects.requireNonNull(builder.currency);
		this.paymentType = Objects.requireNonNull(builder.paymentType);
		this.initiatedBy = Objects.requireNonNull(builder.initiatedBy);
		this.initiatedAt = Objects.requireNonNull(builder.initiatedAt);
		this.sourceAccount = builder.sourceAccount;
		this.targetAccount = builder.targetAccount;
		this.userBehavior = builder.userBehavior;
	}

	public String getPaymentId() {
		return paymentId;
	}

	public String getIdempotencyKey() {
		return idempotencyKey;
	}

	public String getSourceAccountId() {
		return sourceAccountId;
	}

	public String getTargetAccountId() {
		return targetAccountId;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public String getCurrency() {
		return currency;
	}

	public String getPaymentType() {
		return paymentType;
	}

	public String getInitiatedBy() {
		return initiatedBy;
	}

	public Instant getInitiatedAt() {
		return initiatedAt;
	}

	public AccountContext getSourceAccount() {
		return sourceAccount;
	}

	public AccountContext getTargetAccount() {
		return targetAccount;
	}

	public UserBehaviorContext getUserBehavior() {
		return userBehavior;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private String paymentId;
		private String idempotencyKey;
		private String sourceAccountId;
		private String targetAccountId;
		private BigDecimal amount;
		private String currency;
		private String paymentType;
		private String initiatedBy;
		private Instant initiatedAt;
		private AccountContext sourceAccount;
		private AccountContext targetAccount;
		private UserBehaviorContext userBehavior;

		public Builder paymentId(String v) {
			this.paymentId = v;
			return this;
		}

		public Builder idempotencyKey(String v) {
			this.idempotencyKey = v;
			return this;
		}

		public Builder sourceAccountId(String v) {
			this.sourceAccountId = v;
			return this;
		}

		public Builder targetAccountId(String v) {
			this.targetAccountId = v;
			return this;
		}

		public Builder amount(BigDecimal v) {
			this.amount = v;
			return this;
		}

		public Builder currency(String v) {
			this.currency = v;
			return this;
		}

		public Builder paymentType(String v) {
			this.paymentType = v;
			return this;
		}

		public Builder initiatedBy(String v) {
			this.initiatedBy = v;
			return this;
		}

		public Builder initiatedAt(Instant v) {
			this.initiatedAt = v;
			return this;
		}

		public Builder sourceAccount(AccountContext v) {
			this.sourceAccount = v;
			return this;
		}

		public Builder targetAccount(AccountContext v) {
			this.targetAccount = v;
			return this;
		}

		public Builder userBehavior(UserBehaviorContext v) {
			this.userBehavior = v;
			return this;
		}

		public PaymentContext build() {
			return new PaymentContext(this);
		}
	}

	/**
	 * Enriched data about an account - fetched from Account Service.
	 */
	public record AccountContext(
			String accountId,
			String ownerId,
			String currency,
			BigDecimal currentBalance,
			boolean isNew,            // account created less than 30 days ago
			int totalTransactionCount,
			BigDecimal averageTransactionAmount,
			boolean hasPreviousFraudFlags
	) {
	}

	/**
	 * User behavioral pattern - computed from transaction history.
	 */
	public record UserBehaviorContext(
			String userId,
			int transactionsLast1Hour,
			int transactionsLast24Hours,
			BigDecimal totalAmountLast24Hours,
			BigDecimal largestTransactionEver,
			String mostFrequentCurrency,
			boolean hasInternationalTransactions,
			String[] recentCountries        // countries of recent transactions
	) {
	}
}