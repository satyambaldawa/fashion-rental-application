-- The invoices report filters, counts and pages invoices by created_at.
CREATE INDEX idx_invoices_created_at ON invoices(created_at);
