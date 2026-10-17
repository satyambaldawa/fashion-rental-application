-- A receipt has been handed over exactly when it is GIVEN or RETURNED; GENERATED and
-- CANCELLED receipts never have a given_at. Same role as receipts_cancellation_audit_check.
ALTER TABLE receipts
    ADD CONSTRAINT receipts_given_at_check
        CHECK ((status IN ('GIVEN', 'RETURNED')) = (given_at IS NOT NULL));
