-- V1__initial_schema.sql - Fraud Detection Service

CREATE TABLE IF NOT EXISTS fraud_cases
(
    id                UUID         NOT NULL,
    payment_id        VARCHAR(255) NOT NULL,
    source_account_id VARCHAR(255) NOT NULL,
    initiated_by      VARCHAR(255) NOT NULL,
    composite_score   INTEGER      NOT NULL,
    risk_level        VARCHAR(20)  NOT NULL,
    status            VARCHAR(30)  NOT NULL,
    reviewed_by       VARCHAR(255),
    review_notes      TEXT,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    version           BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_fraud_cases PRIMARY KEY (id),
    CONSTRAINT uq_fraud_cases_payment_id UNIQUE (payment_id),
    CONSTRAINT chk_fraud_score CHECK (composite_score BETWEEN 0 AND 100),
    CONSTRAINT chk_fraud_status CHECK (status IN (
                                                  'APPROVED', 'BLOCKED', 'UNDER_REVIEW', 'CONFIRMED_FRAUD'
        ))
);

CREATE INDEX idx_fraud_cases_payment_id ON fraud_cases (payment_id);
CREATE INDEX idx_fraud_cases_status ON fraud_cases (status);
CREATE INDEX idx_fraud_cases_source_acct ON fraud_cases (source_account_id);
CREATE INDEX idx_fraud_cases_created_at ON fraud_cases (created_at DESC);

-- Rule results stored as JSONB for flexible querying and analytics
CREATE TABLE IF NOT EXISTS fraud_rule_results
(
    id            UUID        NOT NULL,
    fraud_case_id UUID        NOT NULL REFERENCES fraud_cases (id),
    rule_name     VARCHAR(50) NOT NULL,
    score         INTEGER     NOT NULL,
    triggered     BOOLEAN     NOT NULL,
    reason        TEXT        NOT NULL,
    evaluated_at  TIMESTAMPTZ NOT NULL,

    CONSTRAINT pk_fraud_rule_results PRIMARY KEY (id)
);

CREATE INDEX idx_rule_results_case_id ON fraud_rule_results (fraud_case_id);
CREATE INDEX idx_rule_results_rule_name ON fraud_rule_results (rule_name);
CREATE INDEX idx_rule_results_triggered ON fraud_rule_results (triggered) WHERE triggered = true;

-- Materialized view for fraud analytics dashboard
CREATE MATERIALIZED VIEW IF NOT EXISTS fraud_daily_stats AS
SELECT DATE_TRUNC('day', created_at) AS day,
       status,
       risk_level,
       COUNT(*)                      AS total_cases,
       AVG(composite_score)          AS avg_score,
       MAX(composite_score)          AS max_score
FROM fraud_cases
GROUP BY DATE_TRUNC('day', created_at), status, risk_level;

CREATE UNIQUE INDEX ON fraud_daily_stats (day, status, risk_level);

COMMENT ON TABLE fraud_cases IS 'Fraud analysis results per payment';
COMMENT ON TABLE fraud_rule_results IS 'Individual rule evaluations for each fraud case';
COMMENT ON MATERIALIZED VIEW fraud_daily_stats IS 'Pre-aggregated analytics - refresh daily';