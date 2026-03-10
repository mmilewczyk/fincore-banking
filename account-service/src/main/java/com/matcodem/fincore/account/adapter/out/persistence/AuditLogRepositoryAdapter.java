package com.matcodem.fincore.account.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.matcodem.fincore.account.domain.model.AccountId;
import com.matcodem.fincore.account.domain.port.out.AuditLogRepository;
import com.matcodem.fincore.account.infrastructure.persistence.entity.AuditLogJpaEntity;
import com.matcodem.fincore.account.infrastructure.persistence.repository.AuditLogJpaRepository;

import lombok.RequiredArgsConstructor;

/**
 * Audit log adapter - immutable audit trail of all account operations.
 * Compliance requirement: every state change must be recorded.
 */
@Component
@RequiredArgsConstructor
public class AuditLogRepositoryAdapter implements AuditLogRepository {

	private final AuditLogJpaRepository auditLogJpaRepository;

	@Override
	public void log(AuditLogRepository.AuditEntry entry) {
		var entity = new AuditLogJpaEntity();
		entity.setId(UUID.randomUUID());
		entity.setAccountId(entry.accountId().value());
		entity.setEventType(entry.eventType());
		entity.setPerformedBy(entry.performedBy());
		entity.setDetails(entry.details());
		entity.setOccurredAt(entry.occurredAt());
		auditLogJpaRepository.save(entity);
	}

	@Override
	public List<AuditEntry> findByAccountId(AccountId accountId) {
		return auditLogJpaRepository.findByAccountIdOrderByOccurredAtDesc(accountId.value())
				.stream()
				.map(this::toEntry)
				.toList();
	}

	@Override
	public List<AuditEntry> findByAccountIdAndDateRange(AccountId accountId, Instant from, Instant to) {
		return auditLogJpaRepository.findByAccountIdAndOccurredAtBetweenOrderByOccurredAtDesc(
				accountId.value(), from, to
		).stream().map(this::toEntry).toList();
	}

	private AuditEntry toEntry(AuditLogJpaEntity entity) {
		return new AuditEntry(
				AccountId.of(entity.getAccountId()),
				entity.getEventType(),
				entity.getPerformedBy(),
				entity.getDetails(),
				entity.getOccurredAt()
		);
	}
}
