CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE customers (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL,
    phone           VARCHAR(20) NOT NULL,
    address         TEXT,
    customer_type   VARCHAR(20) NOT NULL CHECK (customer_type IN ('STUDENT', 'PROFESSIONAL', 'MISC')),
    organization_name VARCHAR(255),
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX idx_customers_phone ON customers(phone);

CREATE TABLE items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL,
    category        VARCHAR(20) NOT NULL CHECK (category IN ('COSTUME','ACCESSORIES','PAGDI','DRESS','ORNAMENTS')),
    item_type       VARCHAR(10) NOT NULL DEFAULT 'INDIVIDUAL' CHECK (item_type IN ('INDIVIDUAL','PACKAGE')),
    size            VARCHAR(50),
    description     TEXT,
    rate            INTEGER NOT NULL CHECK (rate > 0),
    deposit         INTEGER NOT NULL CHECK (deposit >= 0),
    quantity        INTEGER NOT NULL DEFAULT 1 CHECK (quantity >= 0),
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE package_components (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    package_item_id     UUID NOT NULL REFERENCES items(id) ON DELETE CASCADE,
    component_item_id   UUID NOT NULL REFERENCES items(id),
    quantity            INTEGER NOT NULL DEFAULT 1 CHECK (quantity >= 1),
    UNIQUE (package_item_id, component_item_id)
);
CREATE INDEX idx_package_components_package_id ON package_components(package_item_id);
CREATE INDEX idx_package_components_component_id ON package_components(component_item_id);

CREATE TABLE item_photos (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    item_id         UUID NOT NULL REFERENCES items(id) ON DELETE CASCADE,
    url             TEXT NOT NULL,
    thumbnail_url   TEXT NOT NULL,
    sort_order      INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_item_photos_item_id ON item_photos(item_id);

CREATE TABLE receipts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    receipt_number  VARCHAR(30) NOT NULL,
    customer_id     UUID NOT NULL REFERENCES customers(id),
    start_datetime  TIMESTAMPTZ NOT NULL,
    end_datetime    TIMESTAMPTZ NOT NULL,
    rental_days     INTEGER NOT NULL CHECK (rental_days >= 1),
    total_rent      INTEGER NOT NULL DEFAULT 0,
    total_deposit   INTEGER NOT NULL DEFAULT 0,
    grand_total     INTEGER NOT NULL DEFAULT 0,
    status          VARCHAR(10) NOT NULL DEFAULT 'GIVEN' CHECK (status IN ('GIVEN','RETURNED')),
    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX idx_receipts_receipt_number ON receipts(receipt_number);
CREATE INDEX idx_receipts_customer_id ON receipts(customer_id);
CREATE INDEX idx_receipts_status ON receipts(status);
CREATE INDEX idx_receipts_end_datetime ON receipts(end_datetime);

CREATE TABLE receipt_line_items (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    receipt_id          UUID NOT NULL REFERENCES receipts(id) ON DELETE CASCADE,
    item_id             UUID NOT NULL REFERENCES items(id),
    quantity            INTEGER NOT NULL CHECK (quantity >= 1),
    rate_snapshot       INTEGER NOT NULL,
    deposit_snapshot    INTEGER NOT NULL,
    line_rent           INTEGER NOT NULL,
    line_deposit        INTEGER NOT NULL
);
CREATE INDEX idx_receipt_line_items_receipt_id ON receipt_line_items(receipt_id);
CREATE INDEX idx_receipt_line_items_item_id ON receipt_line_items(item_id);

CREATE TABLE invoices (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_number              VARCHAR(30) NOT NULL,
    receipt_id                  UUID NOT NULL UNIQUE REFERENCES receipts(id),
    customer_id                 UUID NOT NULL REFERENCES customers(id),
    return_datetime             TIMESTAMPTZ NOT NULL,
    total_rent                  INTEGER NOT NULL,
    total_deposit_collected     INTEGER NOT NULL,
    total_late_fee              INTEGER NOT NULL DEFAULT 0,
    total_damage_cost           INTEGER NOT NULL DEFAULT 0,
    deposit_to_return           INTEGER NOT NULL DEFAULT 0,
    final_amount                INTEGER NOT NULL DEFAULT 0,
    transaction_type            VARCHAR(10) NOT NULL CHECK (transaction_type IN ('COLLECT','REFUND')),
    payment_method              VARCHAR(10) NOT NULL CHECK (payment_method IN ('CASH','UPI','OTHER')),
    damage_notes                TEXT,
    notes                       TEXT,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX idx_invoices_invoice_number ON invoices(invoice_number);
CREATE INDEX idx_invoices_customer_id ON invoices(customer_id);
CREATE INDEX idx_invoices_return_datetime ON invoices(return_datetime);

CREATE TABLE invoice_line_items (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id              UUID NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    receipt_line_item_id    UUID NOT NULL REFERENCES receipt_line_items(id),
    item_id                 UUID NOT NULL REFERENCES items(id),
    quantity_returned       INTEGER NOT NULL,
    is_damaged              BOOLEAN NOT NULL DEFAULT FALSE,
    damage_percentage       NUMERIC(5,2),
    damage_cost             INTEGER NOT NULL DEFAULT 0,
    late_fee                INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX idx_invoice_line_items_invoice_id ON invoice_line_items(invoice_id);

CREATE TABLE late_fee_rules (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    duration_from_hours INTEGER NOT NULL,
    duration_to_hours   INTEGER,
    penalty_multiplier  NUMERIC(4,2) NOT NULL,
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO late_fee_rules (duration_from_hours, duration_to_hours, penalty_multiplier)
VALUES
    (0,   3,    0.50),
    (3,   6,    0.75),
    (6,   24,   1.00),
    (24,  48,   1.50),
    (48,  NULL, 2.00);
