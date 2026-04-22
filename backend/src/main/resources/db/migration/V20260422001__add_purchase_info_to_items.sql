-- Internal purchase tracking fields for shop owner visibility only.
-- Not exposed in any customer-facing API response.
ALTER TABLE items ADD COLUMN purchase_rate INTEGER;
ALTER TABLE items ADD COLUMN vendor_name    TEXT;
