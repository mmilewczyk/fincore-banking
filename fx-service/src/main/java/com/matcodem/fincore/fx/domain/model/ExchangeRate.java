package com.matcodem.fincore.fx.domain.model;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.matcodem.fincore.fx.domain.event.DomainEvent;
import com.matcodem.fincore.fx.domain.event.ExchangeRatePublishedEvent;
import com.matcodem.fincore.fx.domain.event.ExchangeRateSupersededEvent;

/**
 * ExchangeRate Aggregate Root.
 * <p>
 * Represents a market mid-rate for a currency pair at a point in time,
 * enriched with our bank's spread to produce bid/ask rates for customers.
 * <p>
 * Key concepts:
 * <p>
 * MID RATE — raw market rate from external provider (e.g. ECB, Reuters).
 * EUR/PLN = 4.2850 means 1 EUR = 4.2850 PLN.
 * <p>
 * SPREAD   — our bank's margin expressed in basis points (1 bp = 0.01%).
 * Applied symmetrically around mid: bid = mid*(1 - spread), ask = mid*(1 + spread).
 * e.g. mid=4.2850, spread=50bp → bid=4.2636, ask=4.3064
 * <p>
 * BID RATE — rate at which bank BUYS base currency from customer (customer sells).
 * ASK RATE — rate at which bank SELLS base currency to customer (customer buys).
 * <p>
 * STALE    — rates older than staleness threshold are flagged; conversions with
 * stale rates are rejected (configurable override for low-volume pairs).
 * <p>
 * LIFECYCLE:
 * ACTIVE → SUPERSEDED (when a newer rate replaces it)
 * ACTIVE → STALE      (when staleness threshold exceeded)
 */
public class ExchangeRate {

	private static final MathContext MC = new MathContext(10, RoundingMode.HALF_UP);
	private static final int RATE_SCALE = 6;

	private final ExchangeRateId id;
	private final CurrencyPair pair;
	private final BigDecimal midRate;
	private final BigDecimal bidRate;    // mid * (1 - spread)
	private final BigDecimal askRate;    // mid * (1 + spread)
	private final int spreadBasisPoints;
	private final RateSource source;
	private ExchangeRateStatus status;
	private final Instant fetchedAt;
	private final Instant validUntil;
	private Instant supersededAt;

	private final List<DomainEvent> domainEvents = new ArrayList<>();

	private ExchangeRate(ExchangeRateId id, CurrencyPair pair,
	                     BigDecimal midRate, int spreadBasisPoints,
	                     RateSource source, Instant fetchedAt, Duration validity) {
		this.id = id;
		this.pair = pair;
		this.midRate = midRate.setScale(RATE_SCALE, RoundingMode.HALF_UP);
		this.spreadBasisPoints = spreadBasisPoints;
		this.source = source;
		this.fetchedAt = fetchedAt;
		this.validUntil = fetchedAt.plus(validity);
		this.status = ExchangeRateStatus.ACTIVE;

		BigDecimal spreadFactor = BigDecimal.valueOf(spreadBasisPoints)
				.divide(BigDecimal.valueOf(10_000), MC);

		this.bidRate = midRate.multiply(BigDecimal.ONE.subtract(spreadFactor), MC)
				.setScale(RATE_SCALE, RoundingMode.HALF_UP);
		this.askRate = midRate.multiply(BigDecimal.ONE.add(spreadFactor), MC)
				.setScale(RATE_SCALE, RoundingMode.HALF_UP);
	}

	public static ExchangeRate create(CurrencyPair pair, BigDecimal midRate,
	                                  int spreadBasisPoints, RateSource source,
	                                  Instant fetchedAt, Duration validity) {
		Objects.requireNonNull(pair, "CurrencyPair required");
		Objects.requireNonNull(midRate, "Mid rate required");
		Objects.requireNonNull(source, "Rate source required");
		Objects.requireNonNull(fetchedAt, "FetchedAt required");

		if (midRate.compareTo(BigDecimal.ZERO) <= 0) {
			throw new IllegalArgumentException("Mid rate must be positive: " + midRate);
		}
		if (spreadBasisPoints < 0 || spreadBasisPoints > 5000) {
			throw new IllegalArgumentException("Spread must be 0–5000 bps, got: " + spreadBasisPoints);
		}

		ExchangeRate rate = new ExchangeRate(
				ExchangeRateId.generate(), pair, midRate,
				spreadBasisPoints, source, fetchedAt, validity
		);

		rate.recordEvent(new ExchangeRatePublishedEvent(
				rate.id, pair, midRate, rate.bidRate, rate.askRate, spreadBasisPoints, fetchedAt
		));

		return rate;
	}

	public static ExchangeRate reconstitute(ExchangeRateId id, CurrencyPair pair,
	                                        BigDecimal midRate, BigDecimal bidRate, BigDecimal askRate,
	                                        int spreadBasisPoints, RateSource source,
	                                        ExchangeRateStatus status,
	                                        Instant fetchedAt, Instant validUntil, Instant supersededAt) {
		ExchangeRate rate = new ExchangeRate(
				id, pair, midRate, spreadBasisPoints, source, fetchedAt,
				Duration.between(fetchedAt, validUntil)
		);
		rate.status = status;
		rate.supersededAt = supersededAt;
		return rate;
	}

	/**
	 * Converts an amount from base to quote currency using ask rate (bank sells base).
	 * e.g. convert 100 EUR → PLN: result = 100 * askRate(EUR/PLN)
	 */
	public ConversionResult convert(BigDecimal sourceAmount, ConversionDirection direction) {
		assertActive();

		BigDecimal rate = switch (direction) {
			case BUY_BASE -> askRate;  // customer buys base (e.g. EUR) — bank sells at ask
			case SELL_BASE -> bidRate;  // customer sells base — bank buys at bid
		};

		BigDecimal convertedAmount = sourceAmount.multiply(rate, MC)
				.setScale(pair.getQuote().getDecimalPlaces(), RoundingMode.HALF_UP);

		BigDecimal fee = sourceAmount.multiply(
				BigDecimal.valueOf(spreadBasisPoints).divide(BigDecimal.valueOf(10_000), MC), MC
		).setScale(pair.getQuote().getDecimalPlaces(), RoundingMode.HALF_UP);

		return new ConversionResult(
				pair, sourceAmount, convertedAmount, rate, fee, spreadBasisPoints, fetchedAt
		);
	}

	/**
	 * Marks this rate as superseded by a newer one.
	 * Triggers ExchangeRateSupersededEvent for audit trail.
	 */
	public void supersede(ExchangeRateId newerRateId) {
		if (this.status == ExchangeRateStatus.SUPERSEDED) return;
		this.status = ExchangeRateStatus.SUPERSEDED;
		this.supersededAt = Instant.now();
		recordEvent(new ExchangeRateSupersededEvent(id, pair, newerRateId, supersededAt));
	}

	public boolean isStale() {
		return Instant.now().isAfter(validUntil);
	}

	public boolean isActive() {
		return status == ExchangeRateStatus.ACTIVE && !isStale();
	}

	private void assertActive() {
		if (!isActive()) {
			throw new StaleRateException(
					"Rate for %s is %s (fetchedAt: %s, validUntil: %s)"
							.formatted(pair, status, fetchedAt, validUntil)
			);
		}
	}

	private void recordEvent(DomainEvent event) {
		domainEvents.add(event);
	}

	public List<DomainEvent> pullDomainEvents() {
		List<DomainEvent> events = new ArrayList<>(domainEvents);
		domainEvents.clear();
		return Collections.unmodifiableList(events);
	}

	public ExchangeRateId getId() {
		return id;
	}

	public CurrencyPair getPair() {
		return pair;
	}

	public BigDecimal getMidRate() {
		return midRate;
	}

	public BigDecimal getBidRate() {
		return bidRate;
	}

	public BigDecimal getAskRate() {
		return askRate;
	}

	public int getSpreadBasisPoints() {
		return spreadBasisPoints;
	}

	public RateSource getSource() {
		return source;
	}

	public ExchangeRateStatus getStatus() {
		return status;
	}

	public Instant getFetchedAt() {
		return fetchedAt;
	}

	public Instant getValidUntil() {
		return validUntil;
	}

	public Instant getSupersededAt() {
		return supersededAt;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof ExchangeRate e)) return false;
		return Objects.equals(id, e.id);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id);
	}

	@Override
	public String toString() {
		return "ExchangeRate{%s mid=%s bid=%s ask=%s status=%s}"
				.formatted(pair, midRate, bidRate, askRate, status);
	}

	public record ConversionResult(
			CurrencyPair pair,
			BigDecimal sourceAmount,
			BigDecimal convertedAmount,
			BigDecimal appliedRate,
			BigDecimal fee,
			int spreadBasisPoints,
			Instant rateTimestamp
	) {
		public BigDecimal getEffectiveRate() {
			return appliedRate;
		}
	}

	public enum ConversionDirection {
		BUY_BASE,   // customer wants to acquire base currency
		SELL_BASE   // customer wants to sell base currency
	}

	public static class StaleRateException extends RuntimeException {
		public StaleRateException(String message) {
			super(message);
		}
	}
}
