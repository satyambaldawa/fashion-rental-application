package com.fashionrental.configuration.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

// No `code` field: a coupon's code is immutable after creation. `receipts.coupon_code`
// is a snapshot string with no FK, so renaming would split one coupon's history across
// two codes in the discounts-given report. No `isActive` either — PUT edits the
// coupon's terms; PATCH /status flips activation. One way to do each thing.
public record UpdateCouponRequest(
        @NotBlank String discountType,
        @NotNull @Min(1) Integer value,
        @Min(1) Integer minSubtotal,
        @NotNull OffsetDateTime validFrom,
        @NotNull OffsetDateTime validTo,
        @Min(1) Integer usageLimit
) {}
