package com.matcodem.fincore.account.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.matcodem.fincore.account.infrastructure.persistence.entity.AuditLogJpaEntity;

@Repository
public interface AuditLogJpaRepository extends JpaRepository<AuditLogJpaEntity, UUID> {

	List<AuditLogJpaEntity> findByAccountIdOrderByOccurredAtDesc(UUID accountId);

	List<AuditLogJpaEntity> findByAccountIdAndOccurredAtBetweenOrderByOccurredAtDesc(
			UUID accountId, Instant from, Instant to
	);
}