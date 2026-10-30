-- #179: an OWNER can undo a mistaken "Mark as Given" (GIVEN -> GENERATED) before the booked
-- start. Records who undid it and when, in the same audit style as cancellation.
ALTER TABLE receipts
    ADD COLUMN given_reverted_at         TIMESTAMPTZ,
    ADD COLUMN given_reverted_by_user_id UUID REFERENCES users(id);

-- Deliberately not coupled to status (unlike receipts_given_at_check): the audit must survive
-- a later re-give (GENERATED -> GIVEN) or a later cancel, so it cannot require status = GENERATED.
ALTER TABLE receipts
    ADD CONSTRAINT receipts_given_reverted_audit_check
        CHECK ((given_reverted_at IS NULL) = (given_reverted_by_user_id IS NULL));
