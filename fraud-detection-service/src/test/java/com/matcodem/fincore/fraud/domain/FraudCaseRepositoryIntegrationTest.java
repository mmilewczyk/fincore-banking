package com.matcodem.fincore.fraud.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.matcodem.fincore.fraud.adapter.out.persistence.FraudCaseRepositoryAdapter;
import com.matcodem.fincore.fraud.domain.model.FraudCase;
import com.matcodem.fincore.fraud.domain.model.FraudCaseStatus;
import com.matcodem.fincore.fraud.domain.model.RiskScore;
import com.matcodem.fincore.fraud.domain.model.RuleResult;

@SpringBootTest
@Testcontainers
@DisplayName("FraudCaseRepository Integration Tests")
class FraudCaseRepositoryIntegrationTest {

	@Container
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
			.withDatabaseName("fraud_db")
			.withUsername("fincore")
			.withPassword("fincore_secret");

	@Container
	static KafkaContainer kafka = new KafkaContainer(
			DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

	@DynamicPropertySource
	static void configureProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
		registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
		registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
				() -> "http://localhost:9999/ignored");
	}

	@Autowired
	private FraudCaseRepositoryAdapter repository;

	@Test
	@DisplayName("should persist and reload FraudCase with all rule results")
	void shouldPersistAndReload() {
		FraudCase fraudCase = FraudCase.evaluate(
				"pay-integration-001", "acc-001", "user-001",
				RiskScore.of(65),
				List.of(
						RuleResult.trigger("LARGE_AMOUNT", 40, "Exceeds limit"),
						RuleResult.trigger("VELOCITY_CHECK", 25, "Too many tx"),
						RuleResult.pass("NEW_ACCOUNT")
				)
		);
		fraudCase.pullDomainEvents();

		FraudCase saved = repository.save(fraudCase);

		Optional<FraudCase> found = repository.findById(saved.getId());

		assertThat(found).isPresent();
		FraudCase loaded = found.get();
		assertThat(loaded.getPaymentId()).isEqualTo("pay-integration-001");
		assertThat(loaded.getCompositeScore()).isEqualTo(RiskScore.of(65));
		assertThat(loaded.getStatus()).isEqualTo(FraudCaseStatus.BLOCKED);
		assertThat(loaded.getRuleResults()).hasSize(3);
		assertThat(loaded.getRuleResults())
				.anyMatch(r -> r.ruleName().equals("LARGE_AMOUNT") && r.triggered());
	}

	@Test
	@DisplayName("should find FraudCase by paymentId")
	void shouldFindByPaymentId() {
		FraudCase fraudCase = FraudCase.evaluate(
				"pay-integration-002", "acc-002", "user-002",
				RiskScore.of(15), List.of()
		);
		fraudCase.pullDomainEvents();
		repository.save(fraudCase);

		Optional<FraudCase> found = repository.findByPaymentId("pay-integration-002");

		assertThat(found).isPresent();
		assertThat(found.get().isApproved()).isTrue();
	}

	@Test
	@DisplayName("should find all UNDER_REVIEW cases")
	void shouldFindByStatus() {
		FraudCase mediumRisk = FraudCase.evaluate(
				"pay-integration-003", "acc-003", "user-003",
				RiskScore.of(45), List.of()
		);
		mediumRisk.pullDomainEvents();
		repository.save(mediumRisk);

		List<FraudCase> underReview = repository.findByStatus(FraudCaseStatus.UNDER_REVIEW);

		assertThat(underReview).isNotEmpty();
		assertThat(underReview).allMatch(FraudCase::isUnderReview);
	}
}
