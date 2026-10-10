-- #166: receipts are never deleted; cancelling is a status change plus an audit trail.
ALTER TABLE receipts DROP CONSTRAINT receipts_status_check;
ALTER TABLE receipts
    ADD CONSTRAINT receipts_status_check
        CHECK (status IN ('GIVEN', 'RETURNED', 'CANCELLED'));

ALTER TABLE receipts
    ADD COLUMN cancelled_at               TIMESTAMPTZ,
    ADD COLUMN cancelled_by_user_id       UUID REFERENCES users(id),
    ADD COLUMN cancellation_reason        VARCHAR(32),
    ADD COLUMN cancellation_reason_detail VARCHAR(500);

ALTER TABLE receipts
    ADD CONSTRAINT receipts_cancellation_reason_check
        CHECK (cancellation_reason IS NULL OR cancellation_reason IN
               ('WRONG_ORDER', 'CUSTOMER_DOES_NOT_WANT', 'CHANGE_ORDER_DATES', 'OTHER')),
    -- Audit fields are present iff the receipt is cancelled.
    ADD CONSTRAINT receipts_cancellation_audit_check
        CHECK ((status = 'CANCELLED') = (cancelled_at IS NOT NULL
                                         AND cancelled_by_user_id IS NOT NULL
                                         AND cancellation_reason IS NOT NULL)),
    -- Free text is required for OTHER and only allowed for OTHER.
    ADD CONSTRAINT receipts_cancellation_detail_check
        CHECK (CASE WHEN cancellation_reason = 'OTHER'
                    THEN cancellation_reason_detail IS NOT NULL AND btrim(cancellation_reason_detail) <> ''
                    ELSE cancellation_reason_detail IS NULL END);

-- Daily/monthly reports look up refunds by cancellation day.
CREATE INDEX idx_receipts_cancelled_at ON receipts (cancelled_at) WHERE cancelled_at IS NOT NULL;
