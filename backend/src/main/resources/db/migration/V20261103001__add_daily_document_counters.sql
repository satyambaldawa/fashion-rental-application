-- #189: atomic per-day counters for receipt and invoice numbers. The old COUNT(*)+1 scheme
-- let two concurrent checkouts compute the same number. The backfill uses MAX(suffix) per day,
-- not COUNT, so a gap in today's numbers can never cause a collision after deploy.
CREATE TABLE daily_document_counters (
    series        VARCHAR(20) NOT NULL CHECK (series IN ('RECEIPT', 'INVOICE')),
    counter_date  DATE        NOT NULL,
    last_value    INTEGER     NOT NULL CHECK (last_value >= 1),
    PRIMARY KEY (series, counter_date)
);
ALTER TABLE public.daily_document_counters ENABLE ROW LEVEL SECURITY;

INSERT INTO daily_document_counters (series, counter_date, last_value)
SELECT 'RECEIPT', to_date(substring(receipt_number FROM 3 FOR 8), 'YYYYMMDD'),
       MAX(CAST(substring(receipt_number FROM 12) AS INTEGER))
FROM receipts WHERE receipt_number ~ '^R-[0-9]{8}-[0-9]+$' GROUP BY 2;

INSERT INTO daily_document_counters (series, counter_date, last_value)
SELECT 'INVOICE', to_date(substring(invoice_number FROM 5 FOR 8), 'YYYYMMDD'),
       MAX(CAST(substring(invoice_number FROM 14) AS INTEGER))
FROM invoices WHERE invoice_number ~ '^INV-[0-9]{8}-[0-9]+$' GROUP BY 2;
