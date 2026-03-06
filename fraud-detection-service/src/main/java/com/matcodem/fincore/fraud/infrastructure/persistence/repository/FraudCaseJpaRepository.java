package com.matcodem.fincore.fraud.infrastructure.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.matcodem.fincore.fraud.infrastructure.persistence.entity.FraudCaseJpaEntity;

@Repository
public interface FraudCaseJpaRepository extends JpaRepository<FraudCaseJpaEntity, UUID> {

	Optional<FraudCaseJpaEntity> findByPaymentId(String paymentId);

	List<FraudCaseJpaEntity> findByStatus(String status);

	@Query("SELECT f FROM FraudCaseJpaEntity f WHERE f.status = 'UNDER_REVIEW' ORDER BY f.createdAt ASC")
	List<FraudCaseJpaEntity> findPendingReview();

	boolean existsByPaymentId(String paymentId);
}