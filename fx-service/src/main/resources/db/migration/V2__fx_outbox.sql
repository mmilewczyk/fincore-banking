-- V2__fx_outbox.sql - Transactional Outbox for FX Service
--
-- PROBLEM SOLVED:
--   FxConversionService previously called FxEventPublisher.publishAll() directly
--   inside the @Transactional method. This means:
--
--     BEGIN TRANSACTION
--       INSERT fx_conversions           <- DB write
--       kafkaTemplate.send(...)         <- network call, outside DB transaction
--     COMMIT
--
--   If Kafka is unavailable or the JVM crashes after the Kafka call but before
--   commit, the fx_conversion row is rolled back but the Kafka message was sent
--   (or vice versa). Payment Service receives FxConversionExecutedEvent for a
--   conversion that doesn't exist in the database - data inconsistency.
--
-- SOLUTION:
--   Both the fx_conversion row and the outbox row are written in a single
--   DB transaction. The outbox poller then publishes to Kafka asynchronously.
--
--     BEGIN TRANSACTION
--       INSERT fx_conversions           <- atomic
--       INSERT fx_outbox_messages       <- atomic with above
--     COMMIT
--     (OutboxPoller) -> read PENDING -> send to Kafka -> mark SENT
--
-- TABLE DESIGN NOTES:
--   aggregate_type  - "FxConversion" or "ExchangeRate", used for routing in poller
--   event_type      - mirrors DomainEvent.eventType() for consumer filtering
--   payload         - Avro bytes stored as base64 text (avoids BYTEA complexity
--                     with text-based outbox tooling; overhead is ~33%)
--   retry_count     - incremented on failure, poller stops at max_retry_count
--   processed_at    - set by poller on SENT/DEAD, null for PENDING
--
-- PARTIAL INDEX on status = 'PENDING':
--   The poller only ever queries PENDING rows. A full index on status
--   (3 values: PENDING/SENT/DEAD) would include historical SENT rows, which
--   constitute ~99% of the table. The partial index is 100x smaller and
--   dramatically faster for the poller's hot path.

CREATE TABLE IF NOT EXISTS fx_outbox_messages
(
    id             UUID         NOT NULL,
    aggregate_id   VARCHAR(255) NOT NULL,
    aggregate_type VARCHAR(50)  NOT NULL,
    event_type     VARCHAR(80)  NOT NULL,
    payload        TEXT         NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    retry_count    INTEGER      NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL,
    processed_at   TIMESTAMPTZ,

    CONSTRAINT pk_fx_outbox PRIMARY KEY (id),
    CONSTRAINT chk_fx_outbox_status CHECK (status IN ('PENDING', 'SENT', 'DEAD')),
    CONSTRAINT chk_fx_outbox_retry CHECK (retry_count >= 0)
);

-- Poller query: SELECT ... WHERE status = 'PENDING' ORDER BY created_at ASC FOR UPDATE SKIP LOCKED
-- Partial index keeps this fast even with millions of historical SENT rows
CREATE INDEX idx_fx_outbox_pending
    ON fx_outbox_messages (created_at ASC)
    WHERE status = 'PENDING';

CREATE INDEX idx_fx_outbox_aggregate
    ON fx_outbox_messages (aggregate_id);

COMMENT ON TABLE fx_outbox_messages IS
    'Transactional outbox - FX domain events pending publication to Kafka. '
        'Written atomically with fx_conversions. Poller publishes and marks SENT.';