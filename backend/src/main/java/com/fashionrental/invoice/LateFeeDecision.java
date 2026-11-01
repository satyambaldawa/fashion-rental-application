package com.fashionrental.invoice;

public enum LateFeeDecision {
    APPLY, WAIVE;

    public static LateFeeDecision from(Boolean applyLateFee) {
        return Boolean.FALSE.equals(applyLateFee) ? WAIVE : APPLY;
    }
}
