package com.matcodem.fincore.account.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.matcodem.fincore.account.domain.port.in.OpenAccountUseCase;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("Account Service Integration Tests")
class AccountServiceIntegrationTest {

	@Container
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
			.withDatabaseName("account_db")
			.withUsername("fincore")
			.withPassword("fincore_secret");

	@Container
	static KafkaContainer kafka = new KafkaContainer(
			DockerImageName.parse("confluentinc/cp-kafka:7.6.0")
	);

	@DynamicPropertySource
	static void configureProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
		registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
		// Skip Keycloak in tests
		registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
				() -> "http://localhost:9999/ignored");
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private OpenAccountUseCase openAccountUseCase;

	@Test
	@DisplayName("POST /api/v1/accounts - should open account and return 201")
	void shouldOpenAccount() throws Exception {
		mockMvc.perform(post("/api/v1/accounts")
						.with(jwt().jwt(j -> j
								.subject("user-123")
								.claim("realm_access", java.util.Map.of(
										"roles", java.util.List.of("ROLE_USER")
								))
						))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
                                {
                                    "currency": "PLN"
                                }
                                """))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.currency").value("PLN"))
				.andExpect(jsonPath("$.balance").value(0))
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.iban").isNotEmpty());
	}

	@Test
	@DisplayName("GET /api/v1/accounts/{id} - should return 404 for unknown account")
	void shouldReturn404ForUnknownAccount() throws Exception {
		mockMvc.perform(get("/api/v1/accounts/00000000-0000-0000-0000-000000000000")
						.with(jwt().jwt(j -> j
								.subject("user-123")
								.claim("realm_access", java.util.Map.of(
										"roles", java.util.List.of("ROLE_USER")
								))
						)))
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("POST /api/v1/accounts - should return 401 without JWT")
	void shouldReturn401WithoutJwt() throws Exception {
		mockMvc.perform(post("/api/v1/accounts")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "currency": "PLN" }
								"""))
				.andExpect(status().isUnauthorized());
	}
}