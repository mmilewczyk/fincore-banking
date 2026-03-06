package com.matcodem.fincore.fraud.domain.port.in;

import java.util.List;

import com.matcodem.fincore.fraud.domain.model.FraudCase;
import com.matcodem.fincore.fraud.domain.model.FraudCaseId;

public interface ReviewFraudCaseUseCase {

	FraudCase getCase(FraudCaseId id);

	List<FraudCase> getPendingReviewCases();

	void approveCase(ApproveCommand command);

	void confirmFraud(ConfirmFraudCommand command);

	record ApproveCommand(FraudCaseId caseId, String reviewedBy, String notes) {
	}

	record ConfirmFraudCommand(FraudCaseId caseId, String reviewedBy, String notes) {
	}
}
