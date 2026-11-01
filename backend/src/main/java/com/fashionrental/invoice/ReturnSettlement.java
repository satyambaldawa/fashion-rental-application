package com.fashionrental.invoice;

/**
 * The deposit/late-fee/damage settlement maths for a return, computed once and shared by
 * {@code ReturnService.previewReturn} and {@code ReturnService.processReturn} so the preview
 * and the stored invoice can never drift apart (#184).
 */
public record ReturnSettlement(
        LateFeeDecision decision,
        int calculatedLateFee,
        int appliedLateFee,
        int waivedLateFee,
        int totalDamageCost,
        int totalDeductions,
        int depositToReturn,
        int finalAmount,
        Invoice.TransactionType transactionType) {

    public static ReturnSettlement of(int depositCollected, int calculatedLateFee,
                                       int totalDamageCost, LateFeeDecision decision) {
        int applied = decision == LateFeeDecision.WAIVE ? 0 : calculatedLateFee;
        int waived = calculatedLateFee - applied;
        int deductions = applied + totalDamageCost;
        int depositToReturn = Math.max(0, depositCollected - deductions);
        int balanceOwed = Math.max(0, deductions - depositCollected);

        if (balanceOwed > 0) {
            return new ReturnSettlement(
                    decision, calculatedLateFee, applied, waived, totalDamageCost,
                    deductions, depositToReturn, balanceOwed, Invoice.TransactionType.COLLECT
            );
        }
        return new ReturnSettlement(
                decision, calculatedLateFee, applied, waived, totalDamageCost,
                deductions, depositToReturn, depositToReturn, Invoice.TransactionType.REFUND
        );
    }

    public int chargedLineLateFee(int calculatedLineLateFee) {
        return decision == LateFeeDecision.WAIVE ? 0 : calculatedLineLateFee;
    }
}
