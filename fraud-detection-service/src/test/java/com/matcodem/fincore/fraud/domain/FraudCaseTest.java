package com.matcodem.fincore.fraud.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.matcodem.fincore.fraud.domain.event.FraudCaseApprovedEvent;
import com.matcodem.fincore.fraud.domain.event.FraudCaseBlockedEvent;
import com.matcodem.fincore.fraud.domain.event.FraudCaseEscalatedEvent;
import com.matcodem.fincore.fraud.domain.event.FraudConfirmedEvent;
import com.matcodem.fincore.fraud.domain.model.FraudCase;
import com.matcodem.fincore.fraud.domain.model.FraudCaseStatus;
import com.matcodem.fincore.fraud.domain.model.RiskScore;
import com.matcodem.fincore.fraud.domain.model.RuleResult;

@DisplayName("FraudCase Aggregate")
class FraudCaseTest {

	private static final String PAYMENT_ID = "pay-001";
	private static final String SOURCE_ACCOUNT = "acc-001";
	private static final String INITIATED_BY = "user-001";

	@Nested
	@DisplayName("Evaluation factory")
	class Evaluation {

		@Test
		@DisplayName("LOW score -> APPROVED status + FraudCaseApprovedEvent")
		void lowScoreShouldApprove() {
			FraudCase fc = FraudCase.evaluate(PAYMENT_ID, SOURCE_ACCOUNT, INITIATED_BY,
					RiskScore.of(10), List.of(RuleResult.pass("VELOCITY_CHECK")));

			assertThat(fc.isApproved()).isTrue();
			var events = fc.pullDomainEvents();
			assertThat(events).hasSize(1);
			assertThat(events.getFirst()).isInstanceOf(FraudCaseApprovedEvent.class);
		}

		@Test
		@DisplayName("MEDIUM score -> UNDER_REVIEW status + FraudCaseEscalatedEvent")
		void mediumScoreShouldEscalate() {
			FraudCase fc = FraudCase.evaluate(PAYMENT_ID, SOURCE_ACCOUNT, INITIATED_BY,
					RiskScore.of(45), List.of());

			assertThat(fc.isUnderReview()).isTrue();
			var events = fc.pullDomainEvents();
			assertThat(events.getFirst()).isInstanceOf(FraudCaseEscalatedEvent.class);
		}

		@Test
		@DisplayName("HIGH score -> BLOCKED status + FraudCaseBlockedEvent")
		void highScoreShouldBlock() {
			FraudCase fc = FraudCase.evaluate(PAYMENT_ID, SOURCE_ACCOUNT, INITIATED_BY,
					RiskScore.of(70), List.of(
							RuleResult.trigger("LARGE_AMOUNT", 40, "Too large"),
							RuleResult.trigger("VELOCITY_CHECK", 30, "Too fast")
					));

			assertThat(fc.isBlocked()).isTrue();
			var events = fc.pullDomainEvents();
			assertThat(events.getFirst()).isInstanceOf(FraudCaseBlockedEvent.class);
		}

		@Test
		@DisplayName("CRITICAL score -> BLOCKED + requiresAccountFreeze")
		void criticalScoreShouldRequireFreeze() {
			FraudCase fc = FraudCase.evaluate(PAYMENT_ID, SOURCE_ACCOUNT, INITIATED_BY,
					RiskScore.of(90), List.of());

			assertThat(fc.isBlocked()).isTrue();
			assertThat(fc.requiresAccountFreeze()).isTrue();
		}
	}

	@Nested
	@DisplayName("Compliance review workflow")
	class ReviewWorkflow {

		@Test
		@DisplayName("UNDER_REVIEW -> APPROVED after manual review")
		void shouldApproveAfterReview() {
			FraudCase fc = FraudCase.evaluate(PAYMENT_ID, SOURCE_ACCOUNT, INITIATED_BY,
					RiskScore.of(40), List.of());
			fc.pullDomainEvents();

			fc.approveAfterReview("compliance-officer-1", "Verified with customer");

			assertThat(fc.isApproved()).isTrue();
			assertThat(fc.getReviewedBy()).isEqualTo("compliance-officer-1");
			assertThat(fc.pullDomainEvents()).hasSize(1)
					.first().isInstanceOf(FraudCaseApprovedEvent.class);
		}

		@Test
		@DisplayName("UNDER_REVIEW -> CONFIRMED_FRAUD after confirmation")
		void shouldConfirmFraud() {
			FraudCase fc = FraudCase.evaluate(PAYMENT_ID, SOURCE_ACCOUNT, INITIATED_BY,
					RiskScore.of(40), List.of());
			fc.pullDomainEvents();

			fc.confirmFraud("compliance-officer-1", "Confirmed money laundering");

			assertThat(fc.getStatus()).isEqualTo(FraudCaseStatus.CONFIRMED_FRAUD);
			assertThat(fc.pullDomainEvents()).hasSize(1)
					.first().isInstanceOf(FraudConfirmedEvent.class);
		}

		@Test
		@DisplayName("should reject approveAfterReview when not UNDER_REVIEW")
		void shouldRejectApprovalWhenNotUnderReview() {
			FraudCase fc = FraudCase.evaluate(PAYMENT_ID, SOURCE_ACCOUNT, INITIATED_BY,
					RiskScore.of(10), List.of()); // LOW -> APPROVED

			assertThatThrownBy(() -> fc.approveAfterReview("officer", "notes"))
					.isInstanceOf(IllegalStateException.class)
					.hasMessageContaining("UNDER_REVIEW");
		}
	}
}