package com.matcodem.fincore.payment.infrastructure.idempotency;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Idempotency guard for Kafka consumers.
 * <p>
 * Usage pattern in consumers:
 * <p>
 * if (!idempotencyGuard.tryProcess(eventId, "fraud.case.approved", GROUP_ID)) {
 * log.info("Duplicate event {} - skipping", eventId);
 * ack.acknowledge();
 * return;
 * }
 * // ... process the event
 * <p>
 * Implementation:
 * INSERT INTO processed_events ON CONFLICT DO NOTHING
 * Returns true  -> first time we see this event, safe to process
 * Returns false -> already processed, skip
 * <p>
 * Thread-safe: INSERT is atomic at DB level. Two concurrent pods trying
 * to process the same event will race on the INSERT - one wins, one skips.
 * <p>
 * Retention cleanup runs daily - removes events older than 30 days.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyGuard {

	private final JdbcTemplate jdbcTemplate;

	private static final String INSERT_SQL = """
			INSERT INTO processed_events (event_id, event_type, consumer_group, processed_at)
			VALUES (?, ?, ?, NOW())
			ON CONFLICT (event_id, consumer_group) DO NOTHING
			""";

	private static final String CLEANUP_SQL = """
			DELETE FROM processed_events
			WHERE processed_at < NOW() - INTERVAL '30 days'
			""";

	/**
	 * @return true if this event should be processed (first time seen)
	 * false if already processed (duplicate - skip)
	 */
	public boolean tryProcess(String eventId, String eventType, String consumerGroup) {
		int rowsInserted = jdbcTemplate.update(INSERT_SQL, eventId, eventType, consumerGroup);
		boolean isNew = rowsInserted > 0;

		if (!isNew) {
			log.warn("Duplicate event detected - skipping: eventId={}, type={}, group={}",
					eventId, eventType, consumerGroup);
		}

		return isNew;
	}

	/**
	 * Cleanup old entries daily at 03:00 to keep the table lean.
	 * 30-day window is safely longer than any realistic Kafka retention.
	 */
	@Scheduled(cron = "0 0 3 * * *")
	public void cleanupOldEntries() {
		int deleted = jdbcTemplate.update(CLEANUP_SQL);
		log.info("Idempotency cleanup: deleted {} entries older than 30 days", deleted);
	}
}
