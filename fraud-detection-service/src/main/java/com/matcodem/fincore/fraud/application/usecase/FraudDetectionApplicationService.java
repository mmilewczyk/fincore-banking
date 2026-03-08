package com.matcodem.fincore.fraud.application.usecase;

import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.matcodem.fincore.fraud.domain.model.FraudCase;
import com.matcodem.fincore.fraud.domain.model.FraudCaseId;
import com.matcodem.fincore.fraud.domain.model.FraudCaseStatus;
import com.matcodem.fincore.fraud.domain.model.PaymentContext;
import com.matcodem.fincore.fraud.domain.port.in.AnalysePaymentUseCase;
import com.matcodem.fincore.fraud.domain.port.in.ReviewFraudCaseUseCase;
import com.matcodem.fincore.fraud.domain.port.out.FraudCaseRepository;
import com.matcodem.fincore.fraud.domain.port.out.FraudEventPublisher;
import com.matcodem.fincore.fraud.domain.port.out.PaymentContextEnricher;
import com.matcodem.fincore.fraud.domain.service.FraudRuleEngine;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Fraud Detection Application Service.
 * <p>
 * Orchestration flow for each incoming payment event:
 * <p>
 * 1. Enrich payment context (fetch account info, user behavior from cache/DB)
 * 2. Run FraudRuleEngine - evaluates all rules in priority order
 * 3. Create FraudCase aggregate with composite score and decision
 * 4. Persist the FraudCase
 * 5. Publish domain events (FraudCaseApproved / FraudCaseBlocked / FraudCaseEscalated)
 * <p>
 * The Payment Service listens to FraudCaseBlocked events and rejects the payment.
 * The Notification Service listens to all events for alerting.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FraudDetectionApplicationService implements AnalysePaymentUseCase, ReviewFraudCaseUseCase {

	private final FraudRuleEngine ruleEngine;
	private final FraudCaseRepository fraudCaseRepository;
	private final FraudEventPublisher eventPublisher;
	private final PaymentContextEnricher contextEnricher;
	private final MeterRegistry meterRegistry;

	@Override
	@Transactional
	@Timed(value = "fraud.analysis.duration", description = "Time to complete fraud analysis")
	public FraudCase analyse(PaymentContext rawContext) {
		log.info("Starting fraud analysis for payment: {}", rawContext.getPaymentId());

		// Step 1: Enrich with account context + behavioral history
		PaymentContext enrichedContext = contextEnricher.enrich(rawContext);

		// Step 2: Run all rules through the engine
		FraudRuleEngine.EvaluationResult evaluation = ruleEngine.evaluate(enrichedContext);

		// Step 3: Create fraud case aggregate
		FraudCase fraudCase = FraudCase.evaluate(
				rawContext.getPaymentId(),
				rawContext.getSourceAccountId(),
				rawContext.getInitiatedBy(),
				evaluation.compositeScore(),
				evaluation.ruleResults()
		);

		// Step 4: Persist
		FraudCase saved = fraudCaseRepository.save(fraudCase);

		// Step 5: Publish events
		var events = saved.pullDomainEvents();
		eventPublisher.publishAll(events);

		// Metrics
		meterRegistry.counter("fraud.cases.total",
				"level", evaluation.compositeScore().getLevel().name(),
				"status", saved.getStatus().name()
		).increment();

		log.info("Fraud analysis complete - payment: {}, score: {}, status: {}",
				rawContext.getPaymentId(),
				evaluation.compositeScore(),
				saved.getStatus());

		return saved;
	}

	@Override
	@Transactional(readOnly = true)
	public FraudCase getCase(FraudCaseId id) {
		return fraudCaseRepository.findById(id)
				.orElseThrow(() -> new NoSuchElementException("FraudCase not found: " + id));
	}

	@Override
	@Transactional(readOnly = true)
	public List<FraudCase> getPendingReviewCases() {
		return fraudCaseRepository.findByStatus(FraudCaseStatus.UNDER_REVIEW);
	}

	@Override
	@Transactional
	public void approveCase(ApproveCommand command) {
		log.info("Compliance approving case: {} by {}", command.caseId(), command.reviewedBy());

		FraudCase fraudCase = loadCase(command.caseId());
		fraudCase.approveAfterReview(command.reviewedBy(), command.notes());
		fraudCaseRepository.save(fraudCase);

		eventPublisher.publishAll(fraudCase.pullDomainEvents());
	}

	@Override
	@Transactional
	public void confirmFraud(ConfirmFraudCommand command) {
		log.warn("Compliance confirming FRAUD for case: {} by {}", command.caseId(), command.reviewedBy());

		FraudCase fraudCase = loadCase(command.caseId());
		fraudCase.confirmFraud(command.reviewedBy(), command.notes());
		fraudCaseRepository.save(fraudCase);

		eventPublisher.publishAll(fraudCase.pullDomainEvents());

		meterRegistry.counter("fraud.confirmed.total").increment();
	}

	private FraudCase loadCase(FraudCaseId id) {
		return fraudCaseRepository.findById(id)
				.orElseThrow(() -> new NoSuchElementException("FraudCase not found: " + id));
	}
}
