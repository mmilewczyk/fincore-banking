package com.matcodem.fincore.fraud.adapter.out.persistence;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/**
 * Reads from a local transaction_history table - a denormalized projection
 * maintained by consuming account.credited / account.debited events from Kafka.
 * <p>
 * Why local copy and not call Account Service each time?
 * - Fraud analysis must be fast (< 200ms SLA)
 * - Account Service might be down - fraud analysis must still work
 * - Complex analytical queries (aggregates, time windows) are cheaper locally
 * <p>
 * This is the CQRS read model pattern - write side is in Account Service,
 * read side is a local projection optimized for fraud queries.
 */
@Repository
@RequiredArgsConstructor
public class TransactionHistoryRepository {

	private final JdbcTemplate jdbcTemplate;

	public int countByAccountId(String accountId) {
		Integer count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM transaction_history WHERE account_id = ?",
				Integer.class, accountId
		);
		return count != null ? count : 0;
	}

	public int countSince(String accountId, Instant since) {
		Integer count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM transaction_history WHERE account_id = ? AND occurred_at >= ?",
				Integer.class, accountId, since
		);
		return count != null ? count : 0;
	}

	public BigDecimal totalAmountSince(String accountId, Instant since) {
		BigDecimal total = jdbcTemplate.queryForObject(
				"SELECT COALESCE(SUM(amount), 0) FROM transaction_history WHERE account_id = ? AND occurred_at >= ?",
				BigDecimal.class, accountId, since
		);
		return total != null ? total : BigDecimal.ZERO;
	}

	public BigDecimal averageAmountByAccountId(String accountId) {
		BigDecimal avg = jdbcTemplate.queryForObject(
				"SELECT COALESCE(AVG(amount), 0) FROM transaction_history WHERE account_id = ?",
				BigDecimal.class, accountId
		);
		return avg != null ? avg : BigDecimal.ZERO;
	}

	public BigDecimal largestTransactionEver(String accountId) {
		BigDecimal max = jdbcTemplate.queryForObject(
				"SELECT COALESCE(MAX(amount), 0) FROM transaction_history WHERE account_id = ?",
				BigDecimal.class, accountId
		);
		return max != null ? max : BigDecimal.ZERO;
	}

	public String mostFrequentCurrency(String accountId) {
		try {
			return jdbcTemplate.queryForObject(
					"""
							SELECT currency FROM transaction_history
							WHERE account_id = ?
							GROUP BY currency
							ORDER BY COUNT(*) DESC
							LIMIT 1
							""",
					String.class, accountId
			);
		} catch (Exception ex) {
			return null;
		}
	}

	public boolean hasInternationalTransactions(String accountId) {
		Integer count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM transaction_history WHERE account_id = ? AND currency != 'PLN'",
				Integer.class, accountId
		);
		return count != null && count > 0;
	}

	public boolean hasFraudFlags(String accountId) {
		Integer count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM fraud_cases WHERE source_account_id = ? AND status IN ('BLOCKED','CONFIRMED_FRAUD')",
				Integer.class, accountId
		);
		return count != null && count > 0;
	}
}
