-- Ad-hoc items are products typed in at checkout for a single rental. They are real
-- rentable rows so that receipts, returns and invoices keep a non-null item_id, but
-- they are excluded from the browsable catalogue (see ItemService#listItems).
ALTER TABLE items ADD COLUMN is_ad_hoc BOOLEAN NOT NULL DEFAULT FALSE;
