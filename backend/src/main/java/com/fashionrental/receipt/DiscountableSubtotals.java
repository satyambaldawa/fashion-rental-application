package com.fashionrental.receipt;

// Only the rent bucket is populated today; the sale bucket is a seam for the (not yet
// built) sell-items epic #57. CouponDiscountResolver's signature is already sale-aware
// so that adding sale-subtotal discounting later is additive, not a rework.
public record DiscountableSubtotals(int rent, int sale) {
    public int total() { return rent + sale; }

    // Names the buckets that actually make up total(), so rejection messages stay truthful
    // without being rewritten when #57 starts populating the sale bucket.
    public String label() {
        return sale == 0 ? "rent subtotal" : "rent and sale subtotal";
    }
}
