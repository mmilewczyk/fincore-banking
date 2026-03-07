package com.matcodem.fincore.fx.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.matcodem.fincore.fx.domain.event.DomainEvent;
import com.matcodem.fincore.fx.domain.event.FxConversionExecutedEvent;
import com.matcodem.fincore.fx.domain.event.FxConversionFailedEvent;

/**
 * FxConversion Aggregate Root.
 * <p>
 * Records each completed currency conversion — the contractual agreement
 * between the bank and customer for a specific amount at a specific rate.
 * <p>
 * Once EXECUTED, a conversion is immutable — it represents a financial commitment.
 * Corrections are modelled as reversals + new conversions (audit trail preserved).
 */
public class FxConversion {

	private final FxConversionId id;
	private final String paymentId;
	private final String accountId;
	private final String requestedBy;
	private final CurrencyPair pair;
	private final BigDecimal sourceAmount;
	private final BigDecimal convertedAmount;
	private final BigDecimal appliedRate;
	private final BigDecimal fee;
	private final int spreadBasisPoints;
	private final ExchangeRateId rateSnapshotId;
	private final Instant rateTimestamp;
	private FxConversionStatus status;
	private String failureReason;
	private final Instant createdAt;

	private final List<DomainEvent> domainEvents = new ArrayList<>();

	private FxConversion(FxConversionId id, String paymentId, String accountId,
	                     String requestedBy, CurrencyPair pair,
	                     BigDecimal sourceAmount, BigDecimal convertedAmount,
	                     BigDecimal appliedRate, BigDecimal fee,
	                     int spreadBasisPoints, ExchangeRateId rateSnapshotId,
	                     Instant rateTimestamp, FxConversionStatus status,
	                     String failureReason, Instant createdAt) {
		this.id = id;
		this.paymentId = paymentId;
		this.accountId = accountId;
		this.requestedBy = requestedBy;
		this.pair = pair;
		this.sourceAmount = sourceAmount;
		this.convertedAmount = convertedAmount;
		this.appliedRate = appliedRate;
		this.fee = fee;
		this.spreadBasisPoints = spreadBasisPoints;
		this.rateSnapshotId = rateSnapshotId;
		this.rateTimestamp = rateTimestamp;
		this.status = status;
		this.failureReason = failureReason;
		this.createdAt = createdAt;
	}

	public static FxConversion execute(
			String paymentId, String accountId, String requestedBy,
			ExchangeRate rate, BigDecimal sourceAmount,
			ExchangeRate.ConversionDirection direction) {

		ExchangeRate.ConversionResult result = rate.convert(sourceAmount, direction);

		FxConversionId id = FxConversionId.generate();
		Instant now = Instant.now();

		FxConversion conversion = new FxConversion(
				id, paymentId, accountId, requestedBy,
				result.pair(), sourceAmount, result.convertedAmount(),
				result.appliedRate(), result.fee(), result.spreadBasisPoints(),
				rate.getId(), result.rateTimestamp(),
				FxConversionStatus.EXECUTED, null, now
		);

		conversion.recordEvent(new FxConversionExecutedEvent(
				id, paymentId, accountId, result.pair(),
				sourceAmount, result.convertedAmount(),
				result.appliedRate(), result.fee(), now
		));

		return conversion;
	}

	public static FxConversion failed(
			String paymentId, String accountId, String requestedBy,
			CurrencyPair pair, BigDecimal sourceAmount, String reason) {

		Instant now = Instant.now();
		FxConversion conversion = new FxConversion(
				FxConversionId.generate(), paymentId, accountId, requestedBy,
				pair, sourceAmount, BigDecimal.ZERO,
				BigDecimal.ZERO, BigDecimal.ZERO, 0, null, now,
				FxConversionStatus.FAILED, reason, now
		);

		conversion.recordEvent(new FxConversionFailedEvent(
				conversion.id, paymentId, pair, sourceAmount, reason, now
		));

		return conversion;
	}

	public static FxConversion reconstitute(
			FxConversionId id, String paymentId, String accountId, String requestedBy,
			CurrencyPair pair, BigDecimal sourceAmount, BigDecimal convertedAmount,
			BigDecimal appliedRate, BigDecimal fee, int spreadBasisPoints,
			ExchangeRateId rateSnapshotId, Instant rateTimestamp,
			FxConversionStatus status, String failureReason, Instant createdAt) {
		return new FxConversion(id, paymentId, accountId, requestedBy, pair,
				sourceAmount, convertedAmount, appliedRate, fee, spreadBasisPoints,
				rateSnapshotId, rateTimestamp, status, failureReason, createdAt);
	}

	private void recordEvent(DomainEvent event) {
		domainEvents.add(event);
	}

	public List<DomainEvent> pullDomainEvents() {
		List<DomainEvent> events = new ArrayList<>(domainEvents);
		domainEvents.clear();
		return Collections.unmodifiableList(events);
	}

	public boolean isExecuted() {
		return status == FxConversionStatus.EXECUTED;
	}

	public boolean isFailed() {
		return status == FxConversionStatus.FAILED;
	}

	public FxConversionId getId() {
		return id;
	}

	public String getPaymentId() {
		return paymentId;
	}

	public String getAccountId() {
		return accountId;
	}

	public String getRequestedBy() {
		return requestedBy;
	}

	public CurrencyPair getPair() {
		return pair;
	}

	public BigDecimal getSourceAmount() {
		return sourceAmount;
	}

	public BigDecimal getConvertedAmount() {
		return convertedAmount;
	}

	public BigDecimal getAppliedRate() {
		return appliedRate;
	}

	public BigDecimal getFee() {
		return fee;
	}

	public int getSpreadBasisPoints() {
		return spreadBasisPoints;
	}

	public ExchangeRateId getRateSnapshotId() {
		return rateSnapshotId;
	}

	public Instant getRateTimestamp() {
		return rateTimestamp;
	}

	public FxConversionStatus getStatus() {
		return status;
	}

	public String getFailureReason() {
		return failureReason;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}