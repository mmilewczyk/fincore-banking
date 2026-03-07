package com.matcodem.fincore.fx.infrastructure.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.matcodem.fincore.fx.infrastructure.persistence.entity.FxConversionJpaEntity;

@Repository
public interface FxConversionJpaRepository extends JpaRepository<FxConversionJpaEntity, UUID> {
	Optional<FxConversionJpaEntity> findByPaymentId(String paymentId);

	List<FxConversionJpaEntity> findByAccountIdOrderByCreatedAtDesc(String accountId);
}
