package com.matcodem.fincore.fraud.adapter.in.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Maintains the local transaction_history projection.
 * <p>
 * Listens to account.credited and account.debited events published by Account Service.
 * Writes a denormalized row for each transaction into transaction_history table.
 * <p>
 * This is the CQRS pattern - this service owns its own read model,
 * optimized for fraud analytics queries (velocity, averages, history).
 * <p>
 * Idempotent: uses INSERT ... ON CONFLICT DO NOTHING (event_id as unique key).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionHistoryProjector {

	private final JdbcTemplate jdbcTemplate;
	private final ObjectMapper objectMapper;

	@KafkaListener(
			topics = {
					"fincore.accounts.account-debited",
					"fincore.accounts.account-credited"
			},
			groupId = "fraud-transaction-projector",
			containerFactory = "fraudKafkaListenerContainerFactory"
	)
	public void onAccountTransaction(
			ConsumerRecord<String, String> record,
			Acknowledgment acknowledgment) {

		try {
			@SuppressWarnings("unchecked")
			Map<String, Object> event = objectMapper.readValue(record.value(), Map.class);

			String eventId = event.get("eventId").toString();
			String accountId = event.get("accountId").toString();
			BigDecimal amount = new BigDecimal(event.get("amount").toString());
			String currency = event.getOrDefault("currency", "PLN").toString();
			String reference = event.getOrDefault("reference", "").toString();
			Instant occurredAt = Instant.parse(event.get("occurredAt").toString());

			// Idempotent upsert - safe on Kafka re-delivery
			jdbcTemplate.update("""
							INSERT INTO transaction_history
							    (id, account_id, amount, currency, reference, occurred_at)
							VALUES (?, ?, ?, ?, ?, ?)
							ON CONFLICT (id) DO NOTHING
							""",
					UUID.fromString(eventId), accountId, amount, currency, reference, occurredAt
			);

			log.debug("Projected transaction {} for account {} - amount: {} {}",
					eventId, accountId, amount, currency);

			acknowledgment.acknowledge();

		} catch (Exception ex) {
			log.error("Failed to project transaction event: {}", ex.getMessage(), ex);
			throw new RuntimeException("Projection failed", ex);
		}
	}
}
