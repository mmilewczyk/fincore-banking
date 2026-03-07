package com.matcodem.fincore.fx.infrastructure.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.matcodem.fincore.fx.infrastructure.persistence.entity.ExchangeRateJpaEntity;

@Repository
public interface ExchangeRateJpaRepository extends JpaRepository<ExchangeRateJpaEntity, UUID> {

	@Query("""
			SELECT r FROM ExchangeRateJpaEntity r
			WHERE r.baseCurrency = :base AND r.quoteCurrency = :quote
			  AND r.status = 'ACTIVE'
			ORDER BY r.fetchedAt DESC
			LIMIT 1
			""")
	Optional<ExchangeRateJpaEntity> findActiveByPair(
			@Param("base") String base, @Param("quote") String quote);

	@Query("SELECT r FROM ExchangeRateJpaEntity r WHERE r.status = 'ACTIVE'")
	List<ExchangeRateJpaEntity> findAllActive();

	@Modifying
	@Query("""
			UPDATE ExchangeRateJpaEntity r
			SET r.status = 'SUPERSEDED', r.supersededAt = CURRENT_TIMESTAMP
			WHERE r.baseCurrency = :base AND r.quoteCurrency = :quote
			  AND r.status = 'ACTIVE'
			""")
	void supersedeAllForPair(@Param("base") String base, @Param("quote") String quote);
}
