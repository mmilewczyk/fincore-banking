package com.matcodem.fincore.account.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;

import com.matcodem.fincore.account.domain.model.AccountId;
import com.matcodem.fincore.account.domain.port.out.AuditLogRepository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

/**
 * Audit log adapter — immutable audit trail of all account operations.
 * Compliance requirement: every state change must be recorded.
 */
@Component
@RequiredArgsConstructor
public class AuditLogRepositoryAdapter implements AuditLogRepository {

	private final SpringDataAuditLogRepository springDataRepository;

	@Override
	public void log(AuditLogRepository.AuditEntry entry) {
		var entity = new AuditLogJpaEntity();
		entity.setId(UUID.randomUUID());
		entity.setAccountId(entry.accountId().value());
		entity.setEventType(entry.eventType());
		entity.setPerformedBy(entry.performedBy());
		entity.setDetails(entry.details());
		entity.setOccurredAt(entry.occurredAt());
		springDataRepository.save(entity);
	}

	@Override
	public List<AuditEntry> findByAccountId(AccountId accountId) {
		return springDataRepository.findByAccountIdOrderByOccurredAtDesc(accountId.value())
				.stream()
				.map(this::toEntry)
				.toList();
	}

	@Override
	public List<AuditEntry> findByAccountIdAndDateRange(AccountId accountId, Instant from, Instant to) {
		return springDataRepository.findByAccountIdAndOccurredAtBetweenOrderByOccurredAtDesc(
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

	@Setter
	@Entity
	@Table(name = "audit_log", indexes = {
			@Index(name = "idx_audit_account_id", columnList = "account_id"),
			@Index(name = "idx_audit_occurred_at", columnList = "occurred_at")
	})
	@Getter
	@NoArgsConstructor
	public static class AuditLogJpaEntity {

		@Id
		private UUID id;

		@Column(name = "account_id", nullable = false)
		private UUID accountId;

		@Column(name = "event_type", nullable = false, length = 50)
		private String eventType;

		@Column(name = "performed_by", nullable = false, length = 100)
		private String performedBy;

		@Column(name = "details", columnDefinition = "TEXT")
		private String details;

		@Column(name = "occurred_at", nullable = false)
		private Instant occurredAt;

	}

	@Repository
	interface SpringDataAuditLogRepository extends JpaRepository<AuditLogJpaEntity, UUID> {

		List<AuditLogJpaEntity> findByAccountIdOrderByOccurredAtDesc(UUID accountId);

		List<AuditLogJpaEntity> findByAccountIdAndOccurredAtBetweenOrderByOccurredAtDesc(
				UUID accountId, Instant from, Instant to
		);
	}
}
