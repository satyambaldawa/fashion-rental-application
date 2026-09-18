package com.fashionrental.reporting.model.response;

import java.time.LocalDate;
import java.util.List;

public record DiscountsGivenResponse(
        LocalDate from,
        LocalDate to,
        int totalDiscountGiven,
        int receiptsWithCoupon,
        List<CouponDiscountSummary> byCoupon
) {}
