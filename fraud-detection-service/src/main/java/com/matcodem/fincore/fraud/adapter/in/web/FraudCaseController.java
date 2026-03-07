package com.matcodem.fincore.fraud.adapter.in.web;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.matcodem.fincore.fraud.adapter.in.web.dto.FraudCaseResponse;
import com.matcodem.fincore.fraud.adapter.in.web.dto.ReviewDecisionRequest;
import com.matcodem.fincore.fraud.domain.model.FraudCase;
import com.matcodem.fincore.fraud.domain.model.FraudCaseId;
import com.matcodem.fincore.fraud.domain.port.in.ReviewFraudCaseUseCase;

import io.micrometer.core.annotation.Timed;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST API for fraud case management — used by the compliance dashboard.
 * <p>
 * Endpoints:
 * GET  /api/v1/fraud/cases/{id}       — get a specific case
 * GET  /api/v1/fraud/cases/review     — list all cases pending manual review
 * POST /api/v1/fraud/cases/{id}/approve  — compliance officer approves
 * POST /api/v1/fraud/cases/{id}/confirm  — compliance officer confirms fraud
 * GET  /api/v1/fraud/cases/payment/{paymentId} — lookup by payment
 * <p>
 * Security:
 * GET endpoints      → ROLE_COMPLIANCE or ROLE_ADMIN
 * approve/confirm    → ROLE_COMPLIANCE only (segregation of duties)
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/fraud/cases")
@RequiredArgsConstructor
public class FraudCaseController {

	private final ReviewFraudCaseUseCase reviewUseCase;

	@GetMapping("/{id}")
	@Timed(value = "api.fraud.case.get")
	@PreAuthorize("hasAnyRole('ROLE_COMPLIANCE', 'ROLE_ADMIN')")
	public ResponseEntity<FraudCaseResponse> getCase(@PathVariable String id) {
		FraudCase fraudCase = reviewUseCase.getCase(FraudCaseId.of(id));
		return ResponseEntity.ok(FraudCaseResponse.toResponse(fraudCase));
	}

	@GetMapping("/review")
	@Timed(value = "api.fraud.cases.pending")
	@PreAuthorize("hasAnyRole('ROLE_COMPLIANCE', 'ROLE_ADMIN')")
	public ResponseEntity<List<FraudCaseResponse>> getPendingReview() {
		List<FraudCaseResponse> cases = reviewUseCase.getPendingReviewCases()
				.stream().map(FraudCaseResponse::toResponse).toList();
		log.info("Returning {} pending review cases", cases.size());
		return ResponseEntity.ok(cases);
	}

	@PostMapping("/{id}/approve")
	@Timed(value = "api.fraud.case.approve")
	@PreAuthorize("hasRole('ROLE_COMPLIANCE')")
	public ResponseEntity<FraudCaseResponse> approveCase(
			@PathVariable String id,
			@Valid @RequestBody ReviewDecisionRequest request,
			@AuthenticationPrincipal Jwt jwt) {

		String reviewer = jwt.getSubject();
		log.info("Compliance officer {} approving fraud case {}", reviewer, id);

		reviewUseCase.approveCase(new ReviewFraudCaseUseCase.ApproveCommand(
				FraudCaseId.of(id), reviewer, request.notes()
		));

		FraudCase updated = reviewUseCase.getCase(FraudCaseId.of(id));
		return ResponseEntity.ok(FraudCaseResponse.toResponse(updated));
	}

	@PostMapping("/{id}/confirm-fraud")
	@Timed(value = "api.fraud.case.confirm")
	@PreAuthorize("hasRole('ROLE_COMPLIANCE')")
	public ResponseEntity<FraudCaseResponse> confirmFraud(
			@PathVariable String id,
			@Valid @RequestBody ReviewDecisionRequest request,
			@AuthenticationPrincipal Jwt jwt) {

		String reviewer = jwt.getSubject();
		log.warn("Compliance officer {} confirming FRAUD for case {}", reviewer, id);

		reviewUseCase.confirmFraud(new ReviewFraudCaseUseCase.ConfirmFraudCommand(
				FraudCaseId.of(id), reviewer, request.notes()
		));

		FraudCase updated = reviewUseCase.getCase(FraudCaseId.of(id));
		return ResponseEntity.ok(FraudCaseResponse.toResponse(updated));
	}
}
