package com.matcodem.fincore.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.fincore.fraud.avro.FraudCaseApprovedEvent;
import com.fincore.fraud.avro.FraudCaseBlockedEvent;
import com.matcodem.fincore.payment.domain.port.out.AccountServiceClient;
import com.matcodem.fincore.payment.infrastructure.persistence.entity.OutboxMessageJpaEntity;
import com.matcodem.fincore.payment.infrastructure.persistence.entity.PaymentJpaEntity;
import com.matcodem.fincore.payment.infrastructure.persistence.repository.OutboxJpaRepository;
import com.matcodem.fincore.payment.infrastructure.persistence.repository.PaymentJpaRepository;

/**
 * End-to-End integration test - covers the full payment lifecycle within payment-service.
 * <p>
 * SCOPE:
 * REST initiation -> DB persistence -> outbox event written -> Kafka consumption
 * (simulated fraud approval) -> payment processing -> COMPLETED status in DB
 * <p>
 * WHAT IS REAL:
 * - PostgreSQL (Testcontainers) - real Flyway migrations, real JPA, real outbox table
 * - Redis (Testcontainers) - real Redisson distributed lock acquisition
 * - Kafka (EmbeddedKafka) - real consumer/producer, real topic routing
 * - Spring Security - JWT minted by spring-security-test, real filter chain
 * - OutboxPoller - running on its normal @Scheduled cadence
 * <p>
 * WHAT IS MOCKED:
 * - AccountServiceClient - isolates payment-service boundary; account-service
 * has its own integration test suite. debitAccount/creditAccount are void -
 * mocked with doNothing(). getAccountInfo returns an AccountInfo stub.
 * <p>
 * WHY EMBEDDED KAFKA OVER TESTCONTAINERS KAFKA:
 * EmbeddedKafka starts in the same JVM - no Docker pull, ~5x faster startup.
 * Sufficient for single-service E2E. Cross-service E2E would use Testcontainers Kafka.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@Tag("integration")
@DirtiesContext
@EmbeddedKafka(
		partitions = 1,
		topics = {
				"fincore.payments.payment-initiated",
				"fincore.payments.payment-completed",
				"fincore.payments.payment-failed",
				"fincore.payments.payment-fraud-rejected",
				"fincore.fraud.case-approved",
				"fincore.fraud.case-blocked",
				"fincore.fraud.case-escalated",
				"fincore.fraud.confirmed"
		}
)
@DisplayName("Payment Service - Full Flow E2E Tests")
class PaymentFullFlowE2ETest {

	@Container
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
			.withDatabaseName("payment_db")
			.withUsername("fincore")
			.withPassword("fincore_secret");

	@Container
	@SuppressWarnings("resource")
	static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
			.withExposedPorts(6379)
			.withCommand("redis-server", "--requirepass", "fincore_redis_secret");

	@DynamicPropertySource
	static void configureProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
		registry.add("spring.data.redis.host", redis::getHost);
		registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
		// Disable real JWKS fetch - spring-security-test jwt() processor handles auth
		registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
				() -> "http://localhost:9999/non-existent");
	}

	@Autowired
	MockMvc mockMvc;
	@Autowired
	PaymentJpaRepository paymentRepository;
	@Autowired
	OutboxJpaRepository outboxRepository;
	@Autowired
	KafkaTemplate<String, Object> kafkaTemplate;

	@MockitoBean
	AccountServiceClient accountServiceClient;

	@BeforeEach
	void setUp() {
		// getAccountInfo - returns AccountInfo value object
		when(accountServiceClient.getAccountInfo(anyString()))
				.thenReturn(new AccountServiceClient.AccountInfo("any-account", "PLN", true));

		// debitAccount / creditAccount are void - use doNothing() not when(...).thenReturn()
		doNothing().when(accountServiceClient).debitAccount(anyString(), any(), anyString());
		doNothing().when(accountServiceClient).creditAccount(anyString(), any(), anyString());
	}

	@Test
	@DisplayName("HAPPY PATH: initiate -> fraud approved via Kafka -> COMPLETED in DB")
	void fullFlow_initiateAndFraudApproved_paymentCompletesInDb() throws Exception {
		String idempotencyKey = UUID.randomUUID().toString();
		String sourceAccount = "acc-src-" + UUID.randomUUID();
		String targetAccount = "acc-tgt-" + UUID.randomUUID();

		// Step 1: Initiate payment via REST
		MvcResult result = mockMvc.perform(post("/api/v1/payments")
						.with(userJwt())
						.header("X-Idempotency-Key", idempotencyKey)
						.contentType(MediaType.APPLICATION_JSON)
						.content(transferBody(sourceAccount, targetAccount, "150.00")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andReturn();

		String paymentId = extractPaymentId(result);

		// Step 2: Payment is in DB as PENDING
		Optional<PaymentJpaEntity> created = paymentRepository
				.findByIdempotencyKey(hashOf(idempotencyKey));
		assertThat(created).isPresent();
		assertThat(created.get().getStatus()).isEqualTo("PENDING");

		// Step 3: Outbox has a PENDING event for this payment
		await().atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(100))
				.untilAsserted(() -> {
					List<OutboxMessageJpaEntity> msgs = outboxRepository
							.findAll().stream()
							.filter(m -> paymentId.equals(m.getAggregateId()))
							.toList();
					assertThat(msgs).isNotEmpty();
					assertThat(msgs).anyMatch(m -> m.getEventType().contains("PaymentInitiated"));
				});

		// Step 4: Simulate fraud-detection-service approving the payment
		// In production: fraud-detection-service consumes PaymentInitiated,
		// runs rule engine, publishes FraudCaseApprovedEvent. Here we publish directly.
		FraudCaseApprovedEvent fraudApproved = FraudCaseApprovedEvent.newBuilder()
				.setEventId(UUID.randomUUID().toString())
				.setPaymentId(paymentId)
				.setFraudScore(12)
				.setRulesEvaluated(7)
				.build();

		kafkaTemplate.send(new ProducerRecord<>("fincore.fraud.case-approved", paymentId, fraudApproved));

		// Step 5: FraudDecisionKafkaConsumer -> ProcessPaymentService -> debit + credit -> COMPLETED
		await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(200))
				.untilAsserted(() -> {
					Optional<PaymentJpaEntity> completed = paymentRepository
							.findByIdempotencyKey(hashOf(idempotencyKey));
					assertThat(completed).isPresent();
					assertThat(completed.get().getStatus()).isEqualTo("COMPLETED");
				});

		// Step 6: Outbox now also contains PaymentCompleted event
		await().atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(100))
				.untilAsserted(() -> {
					List<OutboxMessageJpaEntity> msgs = outboxRepository
							.findAll().stream()
							.filter(m -> paymentId.equals(m.getAggregateId()))
							.toList();
					assertThat(msgs).anyMatch(m -> m.getEventType().contains("PaymentCompleted"));
				});
	}

	@Test
	@DisplayName("FRAUD BLOCKED: initiate -> fraud blocked via Kafka -> REJECTED_FRAUD in DB")
	void fullFlow_fraudBlocked_paymentRejectedInDb() throws Exception {
		String idempotencyKey = UUID.randomUUID().toString();

		MvcResult result = mockMvc.perform(post("/api/v1/payments")
						.with(userJwt())
						.header("X-Idempotency-Key", idempotencyKey)
						.contentType(MediaType.APPLICATION_JSON)
						.content(transferBody("acc-fraud-src", "acc-fraud-tgt", "9999.99")))
				.andExpect(status().isCreated())
				.andReturn();

		String paymentId = extractPaymentId(result);

		FraudCaseBlockedEvent fraudBlocked = FraudCaseBlockedEvent.newBuilder()
				.setEventId(UUID.randomUUID().toString())
				.setPaymentId(paymentId)
				.setFraudScore(87)
				.setTriggeringRule("VELOCITY_CHECK")
				.setReason("5 payments from same IP in 60s")
				.build();

		kafkaTemplate.send(new ProducerRecord<>("fincore.fraud.case-blocked", paymentId, fraudBlocked));

		await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(200))
				.untilAsserted(() -> {
					Optional<PaymentJpaEntity> entity = paymentRepository
							.findByIdempotencyKey(hashOf(idempotencyKey));
					assertThat(entity).isPresent();
					assertThat(entity.get().getStatus()).isEqualTo("REJECTED_FRAUD");
					assertThat(entity.get().getFailureReason()).isNotBlank();
				});
	}

	@Test
	@DisplayName("IDEMPOTENCY: same X-Idempotency-Key sent twice -> exactly one payment row in DB")
	void idempotency_sameKeyTwice_exactlyOnePaymentInDb() throws Exception {
		String idempotencyKey = UUID.randomUUID().toString();
		String body = transferBody("acc-idp-src", "acc-idp-tgt", "75.00");

		// First request -> 201
		mockMvc.perform(post("/api/v1/payments")
						.with(userJwt())
						.header("X-Idempotency-Key", idempotencyKey)
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated());

		// Second request - same key -> 200 (idempotent hit)
		mockMvc.perform(post("/api/v1/payments")
						.with(userJwt())
						.header("X-Idempotency-Key", idempotencyKey)
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PENDING"));

		// DB: exactly ONE row for this idempotency key
		long count = paymentRepository.findAll().stream()
				.filter(p -> p.getIdempotencyKey().equals(hashOf(idempotencyKey)))
				.count();
		assertThat(count).isEqualTo(1);
	}

	@Test
	@DisplayName("IDEMPOTENCY: 5 concurrent requests with same key -> exactly one payment in DB")
	void idempotency_concurrentRequests_exactlyOnePaymentInDb() throws Exception {
		String idempotencyKey = UUID.randomUUID().toString();
		String body = transferBody("acc-race-src", "acc-race-tgt", "50.00");

		List<Thread> threads = new java.util.ArrayList<>();
		List<Integer> statusCodes = new java.util.concurrent.CopyOnWriteArrayList<>();

		for (int i = 0; i < 5; i++) {
			threads.add(Thread.ofVirtual().start(() -> {
				try {
					int status = mockMvc.perform(post("/api/v1/payments")
									.with(userJwt())
									.header("X-Idempotency-Key", idempotencyKey)
									.contentType(MediaType.APPLICATION_JSON).content(body))
							.andReturn().getResponse().getStatus();
					statusCodes.add(status);
				} catch (Exception e) {
					statusCodes.add(500);
				}
			}));
		}
		for (Thread t : threads) t.join();

		// Exactly one 201, rest are 200 (idempotent hits)
		long created = statusCodes.stream().filter(s -> s == 201).count();
		assertThat(created).isEqualTo(1);

		// DB: exactly one payment row
		long dbCount = paymentRepository.findAll().stream()
				.filter(p -> p.getIdempotencyKey().equals(hashOf(idempotencyKey)))
				.count();
		assertThat(dbCount).isEqualTo(1);
	}

	@Test
	@DisplayName("OUTBOX: after initiation, outbox row transitions to SENT after poller runs")
	void outbox_afterInitiation_rowMarkedSentByPoller() throws Exception {
		String idempotencyKey = UUID.randomUUID().toString();

		MvcResult result = mockMvc.perform(post("/api/v1/payments")
						.with(userJwt())
						.header("X-Idempotency-Key", idempotencyKey)
						.contentType(MediaType.APPLICATION_JSON)
						.content(transferBody("acc-outbox-src", "acc-outbox-tgt", "200.00")))
				.andExpect(status().isCreated())
				.andReturn();

		String paymentId = extractPaymentId(result);

		// OutboxPoller runs every 500ms - within 5s the row should be SENT
		await().atMost(Duration.ofSeconds(8)).pollInterval(Duration.ofMillis(200))
				.untilAsserted(() -> {
					List<OutboxMessageJpaEntity> msgs = outboxRepository
							.findAll().stream()
							.filter(m -> paymentId.equals(m.getAggregateId()))
							.toList();
					assertThat(msgs).isNotEmpty();
					// At least one SENT row (PaymentInitiated event published to Kafka)
					assertThat(msgs).anyMatch(m -> "SENT".equals(m.getStatus()));
				});
	}

	private org.springframework.test.web.servlet.request.RequestPostProcessor userJwt() {
		return jwt().jwt(j -> j
				.subject("user-e2e-test")
				.claim("realm_access", Map.of("roles", List.of("ROLE_USER"))));
	}

	private String transferBody(String src, String tgt, String amount) {
		return """
				{
				    "sourceAccountId": "%s",
				    "targetAccountId": "%s",
				    "amount": %s,
				    "currency": "PLN",
				    "type": "INTERNAL_TRANSFER"
				}
				""".formatted(src, tgt, amount);
	}

	private String extractPaymentId(MvcResult result) throws Exception {
		String body = result.getResponse().getContentAsString();
		return com.jayway.jsonpath.JsonPath.read(body, "$.id");
	}

	/**
	 * Replicates IdempotencyKey.of() SHA-256 hashing.
	 * Must match the production implementation exactly for DB assertions.
	 */
	private String hashOf(String rawKey) {
		try {
			var digest = java.security.MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(rawKey.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			var sb = new StringBuilder();
			for (byte b : hash) sb.append("%02x".formatted(b));
			return sb.toString().substring(0, 64);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}