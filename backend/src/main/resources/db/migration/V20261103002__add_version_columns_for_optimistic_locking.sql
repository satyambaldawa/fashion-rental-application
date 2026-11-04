-- #189: @Version columns so a stale second save is rejected instead of silently winning.
ALTER TABLE items          ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE customers      ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE receipts       ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE late_fee_rules ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
