package com.matcodem.fincore.fx.adapter.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.matcodem.fincore.fx.domain.model.CurrencyPair;
import com.matcodem.fincore.fx.domain.model.ExchangeRate;
import com.matcodem.fincore.fx.domain.model.ExchangeRateId;
import com.matcodem.fincore.fx.domain.port.out.ExchangeRateRepository;
import com.matcodem.fincore.fx.infrastructure.persistence.mapper.FxPersistenceMapper;
import com.matcodem.fincore.fx.infrastructure.persistence.repository.ExchangeRateJpaRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ExchangeRateRepositoryAdapter implements ExchangeRateRepository {

	private final ExchangeRateJpaRepository exchangeRateJpaRepository;
	private final FxPersistenceMapper mapper;

	@Override
	public ExchangeRate save(ExchangeRate rate) {
		return mapper.toDomain(exchangeRateJpaRepository.save(mapper.toEntity(rate)));
	}

	@Override
	public Optional<ExchangeRate> findActiveByPair(CurrencyPair pair) {
		return exchangeRateJpaRepository.findActiveByPair(
				pair.getBase().getCode(), pair.getQuote().getCode()
		).map(mapper::toDomain);
	}

	@Override
	public Optional<ExchangeRate> findById(ExchangeRateId id) {
		return exchangeRateJpaRepository.findById(id.value()).map(mapper::toDomain);
	}

	@Override
	public List<ExchangeRate> findAllActive() {
		return exchangeRateJpaRepository.findAllActive().stream().map(mapper::toDomain).toList();
	}

	@Override
	@Transactional
	public void supersedeAllForPair(CurrencyPair pair, ExchangeRateId newRateId) {
		exchangeRateJpaRepository.supersedeAllForPair(
				pair.getBase().getCode(), pair.getQuote().getCode()
		);
	}
}
