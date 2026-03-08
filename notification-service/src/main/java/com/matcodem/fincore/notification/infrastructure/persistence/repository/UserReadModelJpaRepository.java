package com.matcodem.fincore.notification.infrastructure.persistence.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.matcodem.fincore.notification.infrastructure.persistence.entity.UserReadModelJpaEntity;

public interface UserReadModelJpaRepository extends JpaRepository<UserReadModelJpaEntity, String> {
	Optional<UserReadModelJpaEntity> findByAccountId(String accountId);
}