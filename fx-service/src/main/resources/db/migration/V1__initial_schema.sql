-- V1__initial_schema.sql — FX Service

CREATE TABLE IF NOT EXISTS exchange_rates
(
    id             UUID           NOT NULL,
    base_currency  VARCHAR(3)     NOT NULL,
    quote_currency VARCHAR(3)     NOT NULL,
    mid_rate       DECIMAL(19, 6) NOT NULL,
    bid_rate       DECIMAL(19, 6) NOT NULL,
    ask_rate       DECIMAL(19, 6) NOT NULL,
    spread_bps     INTEGER        NOT NULL,
    source         VARCHAR(30)    NOT NULL,
    status         VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    fetched_at     TIMESTAMPTZ    NOT NULL,
    valid_until    TIMESTAMPTZ    NOT NULL,
    superseded_at  TIMESTAMPTZ,

    CONSTRAINT pk_exchange_rates PRIMARY KEY (id),
    CONSTRAINT chk_rates_positive CHECK (mid_rate > 0 AND bid_rate > 0 AND ask_rate > 0),
    CONSTRAINT chk_bid_ask CHECK (bid_rate <= mid_rate AND ask_rate >= mid_rate),
    CONSTRAINT chk_spread CHECK (spread_bps >= 0 AND spread_bps <= 5000),
    CONSTRAINT chk_status CHECK (status IN ('ACTIVE', 'SUPERSEDED', 'STALE'))
);

CREATE INDEX idx_rates_pair_status ON exchange_rates (base_currency, quote_currency, status);
CREATE INDEX idx_rates_fetched_at ON exchange_rates (fetched_at DESC);
CREATE INDEX idx_rates_valid_until ON exchange_rates (valid_until) WHERE status = 'ACTIVE';

-- ─── FX Conversions ───────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS fx_conversions
(
    id               UUID           NOT NULL,
    payment_id       VARCHAR(255)   NOT NULL,
    account_id       VARCHAR(255)   NOT NULL,
    requested_by     VARCHAR(255)   NOT NULL,
    base_currency    VARCHAR(3)     NOT NULL,
    quote_currency   VARCHAR(3)     NOT NULL,
    source_amount    DECIMAL(19, 4) NOT NULL,
    converted_amount DECIMAL(19, 4) NOT NULL,
    applied_rate     DECIMAL(19, 6) NOT NULL,
    fee              DECIMAL(19, 4) NOT NULL,
    spread_bps       INTEGER        NOT NULL,
    rate_snapshot_id UUID,
    rate_timestamp   TIMESTAMPTZ    NOT NULL,
    status           VARCHAR(20)    NOT NULL,
    failure_reason   TEXT,
    created_at       TIMESTAMPTZ    NOT NULL,

    CONSTRAINT pk_fx_conversions PRIMARY KEY (id),
    CONSTRAINT uq_fx_conv_payment UNIQUE (payment_id),
    CONSTRAINT chk_fx_status CHECK (status IN ('EXECUTED', 'FAILED')),
    CONSTRAINT fk_fx_conv_rate FOREIGN KEY (rate_snapshot_id)
        REFERENCES exchange_rates (id) ON DELETE SET NULL
);

CREATE INDEX idx_fx_conv_payment ON fx_conversions (payment_id);
CREATE INDEX idx_fx_conv_account ON fx_conversions (account_id);
CREATE INDEX idx_fx_conv_created ON fx_conversions (created_at DESC);

COMMENT ON TABLE exchange_rates IS 'Live exchange rate snapshots with bid/ask spread';
COMMENT ON TABLE fx_conversions IS 'Immutable record of each FX conversion — financial audit trail';
COMMENT ON COLUMN exchange_rates.spread_bps IS '1 bp = 0.01%. Typical: 30-100 bps for retail customers';