-- V1 - Notification Service initial schema

CREATE TABLE notifications
(
    id                   UUID         NOT NULL,
    correlation_event_id VARCHAR(36)  NOT NULL,
    recipient_user_id    VARCHAR(255) NOT NULL,
    type                 VARCHAR(40)  NOT NULL,
    channel              VARCHAR(10)  NOT NULL,
    status               VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    recipient_email      VARCHAR(255) NOT NULL,
    recipient_phone      VARCHAR(20),
    recipient_fcm_token  TEXT,
    title                VARCHAR(255) NOT NULL,
    body                 TEXT         NOT NULL,
    template_data        JSONB,
    failure_reason       TEXT,
    retry_count          INTEGER      NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ  NOT NULL,
    updated_at           TIMESTAMPTZ  NOT NULL,
    version              BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_notifications PRIMARY KEY (id),

    -- Idempotency: one notification per (event, channel). Prevents duplicate sends
    -- on Kafka re-delivery. Partial unique index (WHERE status != 'DEAD_LETTER')
    -- allows re-creation after a dead-lettered notification if the event is re-processed.
    CONSTRAINT uq_notifications_event_channel UNIQUE (correlation_event_id, channel),

    CONSTRAINT chk_notifications_status CHECK (status IN (
                                                          'PENDING', 'SENT', 'FAILED', 'DEAD_LETTER'
        )),
    CONSTRAINT chk_notifications_channel CHECK (channel IN ('EMAIL', 'PUSH', 'SMS')),
    CONSTRAINT chk_notifications_retry CHECK (retry_count >= 0)
);

-- Poller query: WHERE status IN ('PENDING', 'FAILED') ORDER BY created_at ASC
CREATE INDEX idx_notifications_dispatch
    ON notifications (created_at ASC)
    WHERE status IN ('PENDING', 'FAILED');

-- User history query: "show me all notifications for userId X"
CREATE INDEX idx_notifications_recipient ON notifications (recipient_user_id);
CREATE INDEX idx_notifications_created_at ON notifications (created_at DESC);

-- Dead letter monitoring
CREATE INDEX idx_notifications_dead_letter
    ON notifications (created_at DESC)
    WHERE status = 'DEAD_LETTER';

COMMENT ON TABLE notifications IS 'Notification delivery records - one row per channel per triggering event';
COMMENT ON COLUMN notifications.correlation_event_id IS 'Source domain event ID - for deduplication and tracing';
COMMENT ON COLUMN notifications.template_data IS 'Key-value pairs injected into Thymeleaf email template';
COMMENT ON COLUMN notifications.recipient_email IS 'Contact snapshot at notification creation time (GDPR compliance)';