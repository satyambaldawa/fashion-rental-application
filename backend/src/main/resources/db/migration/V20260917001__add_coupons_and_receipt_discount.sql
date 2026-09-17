-- Coupons discount the RENT subtotal of a receipt. The refundable deposit is never
-- discounted: the deposit is not a field on DiscountableSubtotals, so it cannot reach
-- the discount calculation at all. Sale-subtotal discounting waits on #57.
CREATE TABLE coupons (
    id            UUID        PRIMARY KEY,
    code          VARCHAR(32) NOT NULL,
    discount_type VARCHAR(16) NOT NULL,
    value         INTEGER     NOT NULL,
    min_subtotal  INTEGER,
    valid_from    TIMESTAMPTZ NOT NULL,
    valid_to      TIMESTAMPTZ NOT NULL,
    usage_limit   INTEGER,
    times_used    INTEGER     NOT NULL DEFAULT 0,
    is_active     BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL,
    CONSTRAINT coupons_discount_type_check   CHECK (discount_type IN ('PERCENT', 'FIXED')),
    CONSTRAINT coupons_value_positive_check  CHECK (value > 0),
    CONSTRAINT coupons_percent_range_check   CHECK (discount_type <> 'PERCENT' OR value BETWEEN 1 AND 100),
    CONSTRAINT coupons_min_subtotal_check    CHECK (min_subtotal IS NULL OR min_subtotal > 0),
    CONSTRAINT coupons_usage_limit_check     CHECK (usage_limit IS NULL OR usage_limit > 0),
    CONSTRAINT coupons_times_used_check      CHECK (times_used >= 0),
    CONSTRAINT coupons_validity_range_check  CHECK (valid_to > valid_from)
);

-- Codes are normalised (trimmed + uppercased) before persistence, so a plain unique
-- index is sufficient for case-insensitive uniqueness.
CREATE UNIQUE INDEX ux_coupons_code ON coupons (code);

ALTER TABLE receipts
    ADD COLUMN coupon_code     VARCHAR(32),
    ADD COLUMN discount_amount INTEGER NOT NULL DEFAULT 0;

ALTER TABLE receipts
    ADD CONSTRAINT receipts_discount_amount_check
        CHECK (discount_amount >= 0),
    -- A discount without a coupon is impossible. A coupon with a zero discount is legal
    -- (1% of a small subtotal floors to 0), so this is deliberately one-directional.
    ADD CONSTRAINT receipts_discount_requires_coupon_check
        CHECK (coupon_code IS NOT NULL OR discount_amount = 0),
    ADD CONSTRAINT receipts_grand_total_check
        CHECK (grand_total = total_rent - discount_amount + total_deposit);
