package com.matcodem.fincore.notification.infrastructure.persistence.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.matcodem.fincore.notification.infrastructure.persistence.entity.NotificationJpaEntity;

public interface NotificationJpaRepository extends JpaRepository<NotificationJpaEntity, UUID> {

	boolean existsByCorrelationEventIdAndChannel(String correlationEventId, String channel);

	/**
	 * Fetches PENDING and FAILED notifications for dispatch - ordered by created_at
	 * to preserve approximate FIFO ordering across retries.
	 * FAILED included to pick up notifications that failed on previous poller cycle.
	 */
	@Query("""
			SELECT n FROM NotificationJpaEntity n
			WHERE n.status IN ('PENDING', 'FAILED')
			ORDER BY n.createdAt ASC
			LIMIT :limit
			""")
	List<NotificationJpaEntity> findPendingOrFailed(@Param("limit") int limit);
}