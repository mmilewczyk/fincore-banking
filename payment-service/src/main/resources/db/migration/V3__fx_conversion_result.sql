-- ─────────────────────────────────────────────────────────────────────────────
-- V3: Add FX conversion result columns to payments table
--
-- WHY NULLABLE:
--   Only FX_CONVERSION payments have these values.
--   INTERNAL_TRANSFER, EXTERNAL_TRANSFER, BILL_PAYMENT leave them NULL.
--   Flyway cannot add NOT NULL columns without defaults to existing tables
--   without a data migration — nullable is the correct approach here.
--
-- converted_amount + converted_currency:
--   The PLN amount that was credited to the target account.
--   Differs from (amount, currency) which is the source-currency amount the
--   customer initiated (e.g. amount=100 EUR, converted_amount=428.50 PLN).
--   Stored together so we can reconstruct Money value object on read.
--
-- fx_conversion_id:
--   UUID returned by FX Service for this conversion.
--   Required for:
--     - Audit trail (compliance reporting)
--     - Fraud-confirmed reversals (unwinding the FX leg with the counterparty)
--     - Dispute resolution (customer claims wrong rate was applied)
-- ─────────────────────────────────────────────────────────────────────────────

ALTER TABLE payments
    ADD COLUMN converted_amount   NUMERIC(19, 4),
    ADD COLUMN converted_currency CHAR(3),
    ADD COLUMN fx_conversion_id   VARCHAR(36);

-- Partial index: only FX payments will have this populated.
-- Used by compliance queries: "find all FX payments in date range" or
-- "find payment by FX conversion ID" (during dispute resolution).
CREATE INDEX idx_payments_fx_conversion_id
    ON payments (fx_conversion_id)
    WHERE fx_conversion_id IS NOT NULL;

-- Enforce consistency: if one FX column is set, all must be set.
-- Prevents partial writes (e.g. converted_amount set but currency missing).
ALTER TABLE payments
    ADD CONSTRAINT chk_fx_columns_consistent
        CHECK (
            (converted_amount IS NULL AND converted_currency IS NULL AND fx_conversion_id IS NULL)
                OR
            (converted_amount IS NOT NULL AND converted_currency IS NOT NULL AND fx_conversion_id IS NOT NULL)
            );