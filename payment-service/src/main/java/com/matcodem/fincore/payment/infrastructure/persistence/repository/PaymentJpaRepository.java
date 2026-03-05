package com.matcodem.fincore.payment.infrastructure.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.matcodem.fincore.payment.infrastructure.persistence.entity.PaymentJpaEntity;

@Repository
public interface PaymentJpaRepository extends JpaRepository<PaymentJpaEntity, UUID> {

	Optional<PaymentJpaEntity> findByIdempotencyKey(String idempotencyKey);

	List<PaymentJpaEntity> findBySourceAccountId(String accountId);

	List<PaymentJpaEntity> findByTargetAccountId(String accountId);
}