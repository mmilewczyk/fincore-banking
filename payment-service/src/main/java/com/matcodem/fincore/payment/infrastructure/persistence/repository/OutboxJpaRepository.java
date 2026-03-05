package com.matcodem.fincore.payment.infrastructure.persistence.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.matcodem.fincore.payment.infrastructure.persistence.entity.OutboxMessageJpaEntity;

@Repository
public interface OutboxJpaRepository extends JpaRepository<OutboxMessageJpaEntity, UUID> {

	/**
	 * SELECT FOR UPDATE SKIP LOCKED — critical for multi-instance deployments.
	 * <p>
	 * - FOR UPDATE: locks the rows being read
	 * - SKIP LOCKED: skips rows already locked by other instances
	 * <p>
	 * Result: each pod in the cluster processes a different batch of messages.
	 * No duplicates, no blocking between pods.
	 */
	@Query(value = """
			SELECT * FROM outbox_messages
			WHERE status = 'PENDING'
			ORDER BY created_at ASC
			LIMIT :limit
			FOR UPDATE SKIP LOCKED
			""", nativeQuery = true)
	List<OutboxMessageJpaEntity> findPendingMessagesForUpdate(@Param("limit") int limit);
}
