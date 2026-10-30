package com.fashionrental.invoice;

/**
 * The deposit/late-fee/damage settlement maths for a return, computed once and shared by
 * {@code ReturnService.previewReturn} and {@code ReturnService.processReturn} so the preview
 * and the stored invoice can never drift apart (#184).
 */
public record ReturnSettlement(
        int calculatedLateFee,
        int appliedLateFee,   // == calculated, or 0 when waived
        int waivedLateFee,    // == calculated - applied
        int totalDamageCost,
        int totalDeductions,  // applied + damage
        int depositToReturn,  // max(0, deposit - deductions)
        int finalAmount,      // balanceOwed if > 0 else depositToReturn
        Invoice.TransactionType transactionType) {

    public static ReturnSettlement of(int depositCollected, int calculatedLateFee,
                                       int totalDamageCost, Boolean applyLateFee) {
        boolean apply = !Boolean.FALSE.equals(applyLateFee);   // null => apply
        int applied = apply ? calculatedLateFee : 0;
        int waived = calculatedLateFee - applied;
        int deductions = applied + totalDamageCost;
        int depositToReturn = Math.max(0, depositCollected - deductions);
        int balanceOwed = Math.max(0, deductions - depositCollected);

        if (balanceOwed > 0) {
            return new ReturnSettlement(
                    calculatedLateFee, applied, waived, totalDamageCost,
                    deductions, depositToReturn, balanceOwed, Invoice.TransactionType.COLLECT
            );
        }
        return new ReturnSettlement(
                calculatedLateFee, applied, waived, totalDamageCost,
                deductions, depositToReturn, depositToReturn, Invoice.TransactionType.REFUND
        );
    }

    /** Per-line late fee to store/show: the calculated fee, or 0 when waived. */
    public int chargedLineLateFee(int calculatedLineLateFee) {
        return waivedLateFee > 0 ? 0 : calculatedLineLateFee;
    }
}
