package com.matcodem.fincore.fx.infrastructure.persistence.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.matcodem.fincore.fx.infrastructure.persistence.entity.FxOutboxMessageJpaEntity;

@Repository
public interface FxOutboxJpaRepository extends JpaRepository<FxOutboxMessageJpaEntity, UUID> {

	@Query(value = """
			SELECT * FROM fx_outbox_messages
			WHERE status = 'PENDING'
			ORDER BY created_at ASC
			LIMIT :limit
			FOR UPDATE SKIP LOCKED
			""", nativeQuery = true)
	List<FxOutboxMessageJpaEntity> findPendingForUpdate(@Param("limit") int limit);
}
