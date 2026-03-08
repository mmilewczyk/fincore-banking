-- V1__initial_schema.sql
-- Account Service initial schema

CREATE SEQUENCE IF NOT EXISTS iban_sequence
    START WITH 1000000000000001
    INCREMENT BY 1
    NO CYCLE;

CREATE TABLE IF NOT EXISTS accounts
(
    id         UUID           NOT NULL,
    owner_id   VARCHAR(255)   NOT NULL,
    iban       VARCHAR(34)    NOT NULL,
    currency   VARCHAR(3)     NOT NULL,
    balance    DECIMAL(19, 4) NOT NULL DEFAULT 0.0000,
    status     VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ    NOT NULL,
    updated_at TIMESTAMPTZ    NOT NULL,
    version    BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT pk_accounts PRIMARY KEY (id),
    CONSTRAINT uq_accounts_iban UNIQUE (iban),
    CONSTRAINT chk_accounts_balance CHECK (balance >= 0),
    CONSTRAINT chk_accounts_status CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    CONSTRAINT chk_accounts_currency CHECK (currency IN ('PLN', 'EUR', 'USD', 'GBP', 'CHF', 'JPY'))
);

CREATE INDEX idx_accounts_owner_id ON accounts (owner_id);
CREATE INDEX idx_accounts_status ON accounts (status);
CREATE INDEX idx_accounts_iban ON accounts (iban);

CREATE TABLE IF NOT EXISTS audit_log
(
    id           UUID         NOT NULL,
    account_id   UUID         NOT NULL,
    event_type   VARCHAR(50)  NOT NULL,
    performed_by VARCHAR(100) NOT NULL,
    details      TEXT,
    occurred_at  TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_audit_log PRIMARY KEY (id)
);

CREATE INDEX idx_audit_account_id ON audit_log (account_id);
CREATE INDEX idx_audit_occurred_at ON audit_log (occurred_at DESC);

COMMENT ON TABLE accounts IS 'Bank accounts - core aggregate storage';
COMMENT ON TABLE audit_log IS 'Immutable audit trail - compliance requirement';
COMMENT ON SEQUENCE iban_sequence IS 'Used to generate unique BBAN part of Polish IBANs';