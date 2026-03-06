-- V2__transaction_history_projection.sql
-- CQRS read model — local projection of account transactions
-- Maintained by TransactionHistoryProjector consuming Kafka events

CREATE TABLE IF NOT EXISTS transaction_history
(
    id          UUID           NOT NULL,
    account_id  VARCHAR(255)   NOT NULL,
    amount      DECIMAL(19, 4) NOT NULL,
    currency    VARCHAR(3)     NOT NULL DEFAULT 'PLN',
    reference   VARCHAR(255),
    occurred_at TIMESTAMPTZ    NOT NULL,

    CONSTRAINT pk_transaction_history PRIMARY KEY (id)
);

-- Indexes optimised for fraud analytics queries
CREATE INDEX idx_tx_history_account_id ON transaction_history (account_id);
CREATE INDEX idx_tx_history_occurred_at ON transaction_history (account_id, occurred_at DESC);
CREATE INDEX idx_tx_history_currency ON transaction_history (account_id, currency);

COMMENT ON TABLE transaction_history IS
    'CQRS read model — denormalised projection of account transactions. ' ||
    'Populated by consuming account.credited / account.debited Kafka events. ' ||
    'Never write to this table directly — it is owned by the projector.';