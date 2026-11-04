-- #189: a retried return with the same Idempotency-Key replays the first invoice.
ALTER TABLE invoices ADD COLUMN idempotency_key UUID UNIQUE;
