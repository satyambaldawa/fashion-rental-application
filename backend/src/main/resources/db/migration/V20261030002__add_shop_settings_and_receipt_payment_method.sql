-- Owner-configured UPI details used to render a fee-free UPI QR at checkout. Exactly one row
-- (id = 1), seeded here so the app only ever reads/updates it and never has to upsert.
-- upi_vpa NULL means UPI is disabled and the option is hidden at checkout.
CREATE TABLE shop_settings (
    id             SMALLINT     PRIMARY KEY CHECK (id = 1),
    upi_vpa        VARCHAR(256),
    upi_payee_name VARCHAR(100),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT shop_settings_payee_name_required_check
        CHECK (upi_vpa IS NULL OR upi_payee_name IS NOT NULL)
);

INSERT INTO shop_settings (id) VALUES (1);

-- Same RLS hardening as every other public-schema table (see
-- V20260921001__enable_rls_public_tables.sql); the app connects as the table owner and
-- bypasses RLS, so this only denies anon/authenticated.
ALTER TABLE public.shop_settings ENABLE ROW LEVEL SECURITY;

-- How the receipt's grand total was collected. NULL on receipts created before this column
-- existed: their method is genuinely unknown, so they are not backfilled as CASH.
ALTER TABLE receipts
    ADD COLUMN payment_method VARCHAR(10)
        CHECK (payment_method IN ('CASH', 'UPI'));

-- Client-generated key for one UPI confirmation. A retry after a lost response (money already
-- moved) must return the receipt already created, never a second one; UNIQUE also stops a
-- concurrent double tap. NULL for cash checkouts and older receipts.
ALTER TABLE receipts
    ADD COLUMN idempotency_key UUID UNIQUE;
