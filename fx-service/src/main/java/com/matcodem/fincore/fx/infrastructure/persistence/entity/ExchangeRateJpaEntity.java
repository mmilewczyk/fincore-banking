package com.matcodem.fincore.fx.infrastructure.persistence.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "exchange_rates", indexes = {
		@Index(name = "idx_rates_pair_status", columnList = "base_currency, quote_currency, status"),
		@Index(name = "idx_rates_fetched_at", columnList = "fetched_at DESC"),
		@Index(name = "idx_rates_valid_until", columnList = "valid_until")
})
@Getter
@Setter
@NoArgsConstructor
public class ExchangeRateJpaEntity {

	@Id
	private UUID id;

	@Column(name = "base_currency", nullable = false, length = 3)
	private String baseCurrency;
	@Column(name = "quote_currency", nullable = false, length = 3)
	private String quoteCurrency;
	@Column(name = "mid_rate", nullable = false, precision = 19, scale = 6)
	private BigDecimal midRate;
	@Column(name = "bid_rate", nullable = false, precision = 19, scale = 6)
	private BigDecimal bidRate;
	@Column(name = "ask_rate", nullable = false, precision = 19, scale = 6)
	private BigDecimal askRate;
	@Column(name = "spread_bps", nullable = false)
	private int spreadBasisPoints;
	@Column(name = "source", nullable = false, length = 30)
	private String source;
	@Column(name = "status", nullable = false, length = 20)
	private String status;
	@Column(name = "fetched_at", nullable = false)
	private Instant fetchedAt;
	@Column(name = "valid_until", nullable = false)
	private Instant validUntil;
	@Column(name = "superseded_at")
	private Instant supersededAt;
}