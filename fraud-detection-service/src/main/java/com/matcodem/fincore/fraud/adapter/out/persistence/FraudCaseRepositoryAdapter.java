package com.matcodem.fincore.fraud.adapter.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.matcodem.fincore.fraud.domain.model.FraudCase;
import com.matcodem.fincore.fraud.domain.model.FraudCaseId;
import com.matcodem.fincore.fraud.domain.model.FraudCaseStatus;
import com.matcodem.fincore.fraud.domain.port.out.FraudCaseRepository;
import com.matcodem.fincore.fraud.infrastructure.persistence.mapper.FraudCasePersistenceMapper;
import com.matcodem.fincore.fraud.infrastructure.persistence.repository.FraudCaseJpaRepository;

import lombok.RequiredArgsConstructor;

/**
 * Adapter - implements domain FraudCaseRepository port using Spring Data JPA.
 */
@Component
@RequiredArgsConstructor
public class FraudCaseRepositoryAdapter implements FraudCaseRepository {

	private final FraudCaseJpaRepository fraudCaseJpaRepository;
	private final FraudCasePersistenceMapper mapper;

	@Override
	public FraudCase save(FraudCase fraudCase) {
		var entity = mapper.toEntity(fraudCase);
		var saved = fraudCaseJpaRepository.save(entity);
		return mapper.toDomain(saved);
	}

	@Override
	public Optional<FraudCase> findById(FraudCaseId id) {
		return fraudCaseJpaRepository.findById(id.value())
				.map(mapper::toDomain);
	}

	@Override
	public Optional<FraudCase> findByPaymentId(String paymentId) {
		return fraudCaseJpaRepository.findByPaymentId(paymentId)
				.map(mapper::toDomain);
	}

	@Override
	public List<FraudCase> findByStatus(FraudCaseStatus status) {
		return fraudCaseJpaRepository.findByStatus(status.name()).stream()
				.map(mapper::toDomain)
				.toList();
	}
}
