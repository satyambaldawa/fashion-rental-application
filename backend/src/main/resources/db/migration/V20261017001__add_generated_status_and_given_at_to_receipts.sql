-- GENERATED = booked, dress still in the shop; GIVEN = handed to the customer.
ALTER TABLE receipts DROP CONSTRAINT receipts_status_check;
ALTER TABLE receipts
    ADD CONSTRAINT receipts_status_check
        CHECK (status IN ('GENERATED', 'GIVEN', 'RETURNED', 'CANCELLED'));

ALTER TABLE receipts ALTER COLUMN status SET DEFAULT 'GENERATED';
ALTER TABLE receipts ADD COLUMN given_at TIMESTAMPTZ;

-- Everything handed out before this migration is assumed to have left at its booked start.
UPDATE receipts SET given_at = start_datetime WHERE status IN ('GIVEN', 'RETURNED');

-- Rentals starting today (IST) or later are re-labelled as not yet handed over; the
-- shopkeeper taps "Mark as Given" on those already collected. CANCELLED/RETURNED are untouched.
-- IST day boundary is explicit because the database session runs in UTC.
UPDATE receipts
SET status = 'GENERATED', given_at = NULL
WHERE status = 'GIVEN'
  AND start_datetime >= date_trunc('day', now() AT TIME ZONE 'Asia/Kolkata') AT TIME ZONE 'Asia/Kolkata';
