-- Snapshot item details (size, category, description) at receipt creation time
-- so the receipt remains accurate even if the item is later edited.
ALTER TABLE receipt_line_items ADD COLUMN item_size        TEXT;
ALTER TABLE receipt_line_items ADD COLUMN item_category    TEXT;
ALTER TABLE receipt_line_items ADD COLUMN item_description TEXT;
