package com.fashionrental.reporting.model.response;

public record CouponDiscountSummary(
        String couponCode,
        int timesApplied,
        int totalDiscount
) {}
