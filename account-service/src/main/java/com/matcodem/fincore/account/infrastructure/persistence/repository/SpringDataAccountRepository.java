package com.matcodem.fincore.account.infrastructure.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.matcodem.fincore.account.infrastructure.persistence.entity.AccountJpaEntity;

@Repository
public interface SpringDataAccountRepository extends JpaRepository<AccountJpaEntity, UUID> {

	Optional<AccountJpaEntity> findByIban(String iban);

	List<AccountJpaEntity> findByOwnerId(String ownerId);

	boolean existsByIban(String iban);

	@Query("SELECT a FROM AccountJpaEntity a WHERE a.ownerId = :ownerId AND a.status = 'ACTIVE'")
	List<AccountJpaEntity> findActiveAccountsByOwner(String ownerId);
}