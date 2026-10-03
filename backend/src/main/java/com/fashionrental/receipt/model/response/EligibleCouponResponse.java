package com.fashionrental.receipt.model.response;

import java.time.OffsetDateTime;

// Deliberately leaves out id, usageLimit, timesUsed and the created/updated timestamps —
// EXECUTIVE users only need what helps choose a code; the admin view stays behind
// /api/config/coupons.
public record EligibleCouponResponse(
        String code,
        String discountType,
        int value,
        Integer minSubtotal,
        OffsetDateTime validTo,
        int discountAmount
) {}
