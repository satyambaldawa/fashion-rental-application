-- #184: late fee calculated but waived by staff at return time. 0 = not waived (all
-- invoices before this change were charged in full, so no backfill). waived_late_fee > 0 is
-- the only "waived" signal; total_late_fee holds only the fee actually charged.
ALTER TABLE invoices ADD COLUMN waived_late_fee INTEGER NOT NULL DEFAULT 0;
