-- Add share_token to receipts for public (unauthenticated) access via wa.me links
ALTER TABLE receipts ADD COLUMN share_token VARCHAR(12);
UPDATE receipts SET share_token = encode(gen_random_bytes(6), 'hex');
ALTER TABLE receipts ALTER COLUMN share_token SET NOT NULL;
ALTER TABLE receipts ADD CONSTRAINT receipts_share_token_unique UNIQUE (share_token);

-- Add share_token to invoices for public (unauthenticated) access via wa.me links
ALTER TABLE invoices ADD COLUMN share_token VARCHAR(12);
UPDATE invoices SET share_token = encode(gen_random_bytes(6), 'hex');
ALTER TABLE invoices ALTER COLUMN share_token SET NOT NULL;
ALTER TABLE invoices ADD CONSTRAINT invoices_share_token_unique UNIQUE (share_token);
