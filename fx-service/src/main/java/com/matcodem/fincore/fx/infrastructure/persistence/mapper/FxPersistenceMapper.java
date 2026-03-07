package com.matcodem.fincore.fx.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;

import com.matcodem.fincore.fx.domain.model.CurrencyPair;
import com.matcodem.fincore.fx.domain.model.ExchangeRate;
import com.matcodem.fincore.fx.domain.model.ExchangeRateId;
import com.matcodem.fincore.fx.domain.model.ExchangeRateStatus;
import com.matcodem.fincore.fx.domain.model.FxConversion;
import com.matcodem.fincore.fx.domain.model.FxConversionId;
import com.matcodem.fincore.fx.domain.model.FxConversionStatus;
import com.matcodem.fincore.fx.domain.model.RateSource;
import com.matcodem.fincore.fx.infrastructure.persistence.entity.ExchangeRateJpaEntity;
import com.matcodem.fincore.fx.infrastructure.persistence.entity.FxConversionJpaEntity;

@Component
public class FxPersistenceMapper {

	public ExchangeRate toDomain(ExchangeRateJpaEntity e) {
		return ExchangeRate.reconstitute(
				ExchangeRateId.of(e.getId()),
				CurrencyPair.of(e.getBaseCurrency(), e.getQuoteCurrency()),
				e.getMidRate(), e.getBidRate(), e.getAskRate(),
				e.getSpreadBasisPoints(),
				RateSource.valueOf(e.getSource()),
				ExchangeRateStatus.valueOf(e.getStatus()),
				e.getFetchedAt(), e.getValidUntil(), e.getSupersededAt()
		);
	}

	public ExchangeRateJpaEntity toEntity(ExchangeRate r) {
		ExchangeRateJpaEntity e = new ExchangeRateJpaEntity();
		e.setId(r.getId().value());
		e.setBaseCurrency(r.getPair().getBase().getCode());
		e.setQuoteCurrency(r.getPair().getQuote().getCode());
		e.setMidRate(r.getMidRate());
		e.setBidRate(r.getBidRate());
		e.setAskRate(r.getAskRate());
		e.setSpreadBasisPoints(r.getSpreadBasisPoints());
		e.setSource(r.getSource().name());
		e.setStatus(r.getStatus().name());
		e.setFetchedAt(r.getFetchedAt());
		e.setValidUntil(r.getValidUntil());
		e.setSupersededAt(r.getSupersededAt());
		return e;
	}

	public FxConversion toDomain(FxConversionJpaEntity e) {
		return FxConversion.reconstitute(
				FxConversionId.of(e.getId()),
				e.getPaymentId(), e.getAccountId(), e.getRequestedBy(),
				CurrencyPair.of(e.getBaseCurrency(), e.getQuoteCurrency()),
				e.getSourceAmount(), e.getConvertedAmount(),
				e.getAppliedRate(), e.getFee(), e.getSpreadBasisPoints(),
				e.getRateSnapshotId() != null ? ExchangeRateId.of(e.getRateSnapshotId()) : null,
				e.getRateTimestamp(),
				FxConversionStatus.valueOf(e.getStatus()),
				e.getFailureReason(), e.getCreatedAt()
		);
	}

	public FxConversionJpaEntity toEntity(FxConversion c) {
		FxConversionJpaEntity e = new FxConversionJpaEntity();
		e.setId(c.getId().value());
		e.setPaymentId(c.getPaymentId());
		e.setAccountId(c.getAccountId());
		e.setRequestedBy(c.getRequestedBy());
		e.setBaseCurrency(c.getPair().getBase().getCode());
		e.setQuoteCurrency(c.getPair().getQuote().getCode());
		e.setSourceAmount(c.getSourceAmount());
		e.setConvertedAmount(c.getConvertedAmount());
		e.setAppliedRate(c.getAppliedRate());
		e.setFee(c.getFee());
		e.setSpreadBasisPoints(c.getSpreadBasisPoints());
		e.setRateSnapshotId(c.getRateSnapshotId() != null ? c.getRateSnapshotId().value() : null);
		e.setRateTimestamp(c.getRateTimestamp());
		e.setStatus(c.getStatus().name());
		e.setFailureReason(c.getFailureReason());
		e.setCreatedAt(c.getCreatedAt());
		return e;
	}
}