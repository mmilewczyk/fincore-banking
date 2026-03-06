package com.matcodem.fincore.fraud.domain.port.out;

import java.util.List;
import java.util.Optional;

import com.matcodem.fincore.fraud.domain.model.FraudCase;
import com.matcodem.fincore.fraud.domain.model.FraudCaseId;
import com.matcodem.fincore.fraud.domain.model.FraudCaseStatus;

public interface FraudCaseRepository {
	FraudCase save(FraudCase fraudCase);

	Optional<FraudCase> findById(FraudCaseId id);

	Optional<FraudCase> findByPaymentId(String paymentId);

	List<FraudCase> findByStatus(FraudCaseStatus status);
}
