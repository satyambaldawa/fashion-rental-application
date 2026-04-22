-- Reverted: item size/category/description are read from the items table directly
-- via the existing FK relationship, so snapshot columns are not needed.
ALTER TABLE receipt_line_items DROP COLUMN IF EXISTS item_size;
ALTER TABLE receipt_line_items DROP COLUMN IF EXISTS item_category;
ALTER TABLE receipt_line_items DROP COLUMN IF EXISTS item_description;
