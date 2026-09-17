package com.fashionrental.receipt;

import com.fashionrental.configuration.Coupon;

public record AppliedDiscount(Coupon coupon, String code, int amount) {
    public static AppliedDiscount none() { return new AppliedDiscount(null, null, 0); }
    public boolean isApplied() { return coupon != null; }
}
