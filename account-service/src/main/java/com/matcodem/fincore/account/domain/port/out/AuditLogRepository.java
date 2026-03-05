package com.matcodem.fincore.account.domain.port.out;

import java.time.Instant;
import java.util.List;

import com.matcodem.fincore.account.domain.model.AccountId;

/**
 * Driven port — audit log persistence.
 * Every state change is recorded as an immutable audit entry.
 */
public interface AuditLogRepository {

	void log(AuditEntry entry);

	List<AuditEntry> findByAccountId(AccountId accountId);

	List<AuditEntry> findByAccountIdAndDateRange(AccountId accountId, Instant from, Instant to);

	record AuditEntry(
			AccountId accountId,
			String eventType,
			String performedBy,
			String details,
			Instant occurredAt
	) {
	}
}