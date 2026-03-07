-- V2__idempotency_table.sql
--
-- Idempotency guard for Kafka consumers.
--
-- Problem: Kafka guarantees at-least-once delivery. Without this table,
-- a redelivered "fraud.case.approved" event would debit/credit accounts twice.
--
-- Solution: Before processing any event, INSERT the event_id.
-- If it already exists (UNIQUE constraint) → silently skip.
-- INSERT ... ON CONFLICT DO NOTHING returns 0 rows affected → consumer skips.
--
-- Retention: processed events older than 30 days are cleaned up by a scheduled job
-- (ProcessedEventCleanupJob). 30 days is chosen to be longer than any realistic
-- Kafka retention period, so we'll never miss a duplicate.

CREATE TABLE IF NOT EXISTS processed_events
(
    event_id       VARCHAR(255) NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    consumer_group VARCHAR(100) NOT NULL,
    processed_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    -- Composite PK: same event_id can legitimately come from different consumer groups
    CONSTRAINT pk_processed_events PRIMARY KEY (event_id, consumer_group)
);

-- Index for cleanup job (DELETE WHERE processed_at < NOW() - INTERVAL '30 days')
CREATE INDEX idx_processed_events_cleanup ON processed_events (processed_at);

COMMENT ON TABLE processed_events IS
    'Idempotency guard for Kafka consumers. Prevents double-processing of redelivered events.';