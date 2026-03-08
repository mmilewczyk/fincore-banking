-- V2 - User contact read-model
--
-- WHY THIS TABLE EXISTS:
--   notification-service must resolve account-id -> user contact details (email, phone, FCM token)
--   when processing AccountDebited / AccountCredited / AccountFrozen events.
--   These events carry accountId, not userId - so we need a local mapping.
--
--   We do NOT call account-service synchronously on the Kafka hot path because:
--     1. Synchronous HTTP on a consumer creates latency and availability coupling
--     2. account-service might be down; notification-service must not stall
--     3. A local read-model survives account-service restarts without data loss
--
--   This table is populated by UserReadModelConsumer listening to AccountCreatedEvent.
--   It is updated on AccountUpdatedEvent (email/phone changes) and
--   FcmTokenRegisteredEvent (mobile app token refresh).
--
-- CONSISTENCY:
--   Eventual consistency - updates lag by the Kafka consumer offset lag.
--   Acceptable: notifications for an account frozen event will still use the
--   contact valid at event-consumption time (GDPR: snapshot semantics, not live lookup).

CREATE TABLE notification_user_read_model
(
    user_id      VARCHAR(255) NOT NULL,
    account_id   VARCHAR(255) NOT NULL,
    email        VARCHAR(255) NOT NULL,
    phone_number VARCHAR(30),
    fcm_token    TEXT,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_user_read_model PRIMARY KEY (user_id),
    CONSTRAINT uq_user_rm_account_id UNIQUE (account_id)
);

-- Lookup path 1: resolveByUserId  - direct PK hit, no index needed
-- Lookup path 2: resolveByAccountId
CREATE INDEX idx_user_rm_account_id ON notification_user_read_model (account_id);

COMMENT ON TABLE notification_user_read_model IS 'Local account->user contact mapping, populated from AccountCreatedEvent';
COMMENT ON COLUMN notification_user_read_model.user_id IS 'Matches JWT sub claim and payment.initiatedBy';
COMMENT ON COLUMN notification_user_read_model.account_id IS 'Account owned by this user - one row per account';
COMMENT ON COLUMN notification_user_read_model.fcm_token IS 'Firebase Cloud Messaging token - rotated by mobile app, null if user has no mobile app';