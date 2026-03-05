-- V1__initial_schema.sql — Payment Service

CREATE TABLE IF NOT EXISTS payments
(
    id                UUID           NOT NULL,
    idempotency_key   VARCHAR(64)    NOT NULL,
    source_account_id VARCHAR(255)   NOT NULL,
    target_account_id VARCHAR(255)   NOT NULL,
    amount            DECIMAL(19, 4) NOT NULL,
    currency          VARCHAR(3)     NOT NULL,
    type              VARCHAR(30)    NOT NULL,
    status            VARCHAR(30)    NOT NULL DEFAULT 'PENDING',
    failure_reason    TEXT,
    initiated_by      VARCHAR(255)   NOT NULL,
    created_at        TIMESTAMPTZ    NOT NULL,
    updated_at        TIMESTAMPTZ    NOT NULL,
    version           BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT pk_payments PRIMARY KEY (id),
    CONSTRAINT uq_payments_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT chk_payments_amount CHECK (amount > 0),
    CONSTRAINT chk_payments_status CHECK (status IN (
                                                     'PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED',
                                                     'REJECTED_FRAUD'
        )),
    CONSTRAINT chk_payments_accounts CHECK (source_account_id <> target_account_id)
);

CREATE INDEX idx_payments_idempotency_key ON payments (idempotency_key);
CREATE INDEX idx_payments_source_account ON payments (source_account_id);
CREATE INDEX idx_payments_target_account ON payments (target_account_id);
CREATE INDEX idx_payments_status ON payments (status);
CREATE INDEX idx_payments_initiated_by ON payments (initiated_by);
CREATE INDEX idx_payments_created_at ON payments (created_at DESC);

CREATE TABLE IF NOT EXISTS outbox_messages
(
    id             UUID         NOT NULL,
    aggregate_id   VARCHAR(255) NOT NULL,
    aggregate_type VARCHAR(50)  NOT NULL,
    event_type     VARCHAR(50)  NOT NULL,
    payload        TEXT         NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    retry_count    INTEGER      NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL,
    processed_at   TIMESTAMPTZ,

    CONSTRAINT pk_outbox_messages PRIMARY KEY (id),
    CONSTRAINT chk_outbox_status CHECK (status IN ('PENDING', 'SENT', 'DEAD_LETTER')),
    CONSTRAINT chk_outbox_retry CHECK (retry_count >= 0)
);

CREATE INDEX idx_outbox_status ON outbox_messages (status) WHERE status = 'PENDING';
CREATE INDEX idx_outbox_aggregate ON outbox_messages (aggregate_id);
CREATE INDEX idx_outbox_created_at ON outbox_messages (created_at ASC) WHERE status = 'PENDING';

COMMENT ON TABLE payments IS 'Payment aggregates — core domain';
COMMENT ON TABLE outbox_messages IS 'Transactional outbox — guarantees Kafka delivery';
COMMENT ON COLUMN outbox_messages.status IS 'PENDING=awaiting publish, SENT=published, DEAD_LETTER=max retries exceeded';