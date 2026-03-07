package com.matcodem.fincore.fx.adapter.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.matcodem.fincore.fx.domain.model.FxConversion;
import com.matcodem.fincore.fx.domain.model.FxConversionId;
import com.matcodem.fincore.fx.domain.port.out.FxConversionRepository;
import com.matcodem.fincore.fx.infrastructure.persistence.mapper.FxPersistenceMapper;
import com.matcodem.fincore.fx.infrastructure.persistence.repository.FxConversionJpaRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class FxConversionRepositoryAdapter implements FxConversionRepository {

	private final FxConversionJpaRepository fxConversionJpaRepository;
	private final FxPersistenceMapper mapper;

	@Override
	public FxConversion save(FxConversion c) {
		return mapper.toDomain(fxConversionJpaRepository.save(mapper.toEntity(c)));
	}

	@Override
	public Optional<FxConversion> findById(FxConversionId id) {
		return fxConversionJpaRepository.findById(id.value()).map(mapper::toDomain);
	}

	@Override
	public List<FxConversion> findByAccountId(String accountId) {
		return fxConversionJpaRepository.findByAccountIdOrderByCreatedAtDesc(accountId).stream()
				.map(mapper::toDomain).toList();
	}

	@Override
	public Optional<FxConversion> findByPaymentId(String paymentId) {
		return fxConversionJpaRepository.findByPaymentId(paymentId).map(mapper::toDomain);
	}
}
