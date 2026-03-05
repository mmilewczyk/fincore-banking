package com.matcodem.fincore.account.infrastructure.persistence.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "accounts", indexes = {
		@Index(name = "idx_accounts_owner_id", columnList = "owner_id"),
		@Index(name = "idx_accounts_iban", columnList = "iban", unique = true),
		@Index(name = "idx_accounts_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
public class AccountJpaEntity {

	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "owner_id", nullable = false)
	private String ownerId;

	@Column(name = "iban", nullable = false, unique = true, length = 34)
	private String iban;

	@Column(name = "currency", nullable = false, length = 3)
	private String currency;

	@Column(name = "balance", nullable = false, precision = 19, scale = 4)
	private BigDecimal balance;

	@Column(name = "status", nullable = false, length = 20)
	@Enumerated(EnumType.STRING)
	private AccountStatusJpa status;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(name = "version", nullable = false)
	private long version;

	public enum AccountStatusJpa {
		ACTIVE, FROZEN, CLOSED
	}
}