package com.fashionrental.invoice;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ReturnSettlementTest {

    @Test
    void should_refund_full_deposit_when_late_fee_is_zero() {
        ReturnSettlement s = ReturnSettlement.of(1000, 0, 0, null);

        assertThat(s.transactionType()).isEqualTo(Invoice.TransactionType.REFUND);
        assertThat(s.finalAmount()).isEqualTo(1000);
        assertThat(s.appliedLateFee()).isZero();
        assertThat(s.waivedLateFee()).isZero();
    }

    @Test
    void should_refund_remainder_when_late_fee_below_deposit() {
        ReturnSettlement s = ReturnSettlement.of(1000, 300, 0, null);

        assertThat(s.transactionType()).isEqualTo(Invoice.TransactionType.REFUND);
        assertThat(s.finalAmount()).isEqualTo(700);
    }

    @Test
    void should_collect_balance_when_late_fee_above_deposit() {
        ReturnSettlement s = ReturnSettlement.of(1000, 1300, 0, Boolean.TRUE);

        assertThat(s.transactionType()).isEqualTo(Invoice.TransactionType.COLLECT);
        assertThat(s.finalAmount()).isEqualTo(300);
    }

    @Test
    void should_waive_full_late_fee_when_apply_is_false() {
        ReturnSettlement s = ReturnSettlement.of(1000, 300, 0, Boolean.FALSE);

        assertThat(s.appliedLateFee()).isZero();
        assertThat(s.waivedLateFee()).isEqualTo(300);
        assertThat(s.transactionType()).isEqualTo(Invoice.TransactionType.REFUND);
        assertThat(s.finalAmount()).isEqualTo(1000);
    }

    @ParameterizedTest(name = "deposit={0}, lateFee={1}, damage={2}, apply={3} → {4} {5}")
    @CsvSource({
            "1000, 300, 1200, false, COLLECT, 200",
            "1000, 300, 1200, true,  COLLECT, 500",
            "1000, 300, 800,  true,  COLLECT, 100",
            "1000, 300, 800,  false, REFUND,  200",
    })
    void worked_examples(int deposit, int lateFee, int damage, boolean apply,
                          String expectedType, int expectedFinal) {
        ReturnSettlement s = ReturnSettlement.of(deposit, lateFee, damage, apply);

        assertThat(s.transactionType()).isEqualTo(Invoice.TransactionType.valueOf(expectedType));
        assertThat(s.finalAmount()).isEqualTo(expectedFinal);
    }

    @Test
    void should_apply_late_fee_when_flag_is_null() {
        ReturnSettlement withNull = ReturnSettlement.of(1000, 300, 0, null);
        ReturnSettlement withTrue = ReturnSettlement.of(1000, 300, 0, Boolean.TRUE);

        assertThat(withNull.appliedLateFee()).isEqualTo(withTrue.appliedLateFee());
        assertThat(withNull.waivedLateFee()).isEqualTo(withTrue.waivedLateFee());
        assertThat(withNull.finalAmount()).isEqualTo(withTrue.finalAmount());
    }

    @Test
    void should_report_zero_waived_when_fee_is_zero_even_if_waived() {
        ReturnSettlement s = ReturnSettlement.of(1000, 0, 500, Boolean.FALSE);

        assertThat(s.waivedLateFee()).isZero();
        assertThat(s.transactionType()).isEqualTo(Invoice.TransactionType.REFUND);
        assertThat(s.finalAmount()).isEqualTo(500);
    }

    @Test
    void chargedLineLateFee_returns_calculated_value_when_applied_and_zero_when_waived() {
        ReturnSettlement applied = ReturnSettlement.of(1000, 300, 0, Boolean.TRUE);
        ReturnSettlement waived = ReturnSettlement.of(1000, 300, 0, Boolean.FALSE);

        assertThat(applied.chargedLineLateFee(300)).isEqualTo(300);
        assertThat(waived.chargedLineLateFee(300)).isZero();
    }

    @Test
    void damage_only_refunds_remainder_same_as_before() {
        ReturnSettlement s = ReturnSettlement.of(1000, 0, 400, null);

        assertThat(s.transactionType()).isEqualTo(Invoice.TransactionType.REFUND);
        assertThat(s.finalAmount()).isEqualTo(600);
    }

    // Boundary: calculatedLateFee exactly equals depositCollected (deductions == deposit), so
    // balanceOwed = max(0, deductions - deposit) == 0 and depositToReturn = max(0, deposit -
    // deductions) == 0. This is the classic off-by-one risk in the two Math.max(0, ...) calls:
    // a wrong "> 0" vs ">= 0" comparison on balanceOwed would wrongly flip this to COLLECT 0
    // instead of REFUND 0.
    @Test
    void should_refund_nothing_when_applied_late_fee_exactly_exhausts_the_deposit() {
        ReturnSettlement s = ReturnSettlement.of(1000, 1000, 0, Boolean.TRUE);

        assertThat(s.transactionType()).isEqualTo(Invoice.TransactionType.REFUND);
        assertThat(s.appliedLateFee()).isEqualTo(1000);
        assertThat(s.waivedLateFee()).isZero();
        assertThat(s.totalDeductions()).isEqualTo(1000);
        assertThat(s.depositToReturn()).isZero();
        assertThat(s.finalAmount()).isZero();
    }

    // Same boundary, but waived: the fee that would have exactly exhausted the deposit is
    // waived instead, so the full deposit is refunded rather than nothing.
    @Test
    void should_refund_full_deposit_when_the_late_fee_that_would_exactly_exhaust_it_is_waived() {
        ReturnSettlement s = ReturnSettlement.of(1000, 1000, 0, Boolean.FALSE);

        assertThat(s.transactionType()).isEqualTo(Invoice.TransactionType.REFUND);
        assertThat(s.appliedLateFee()).isZero();
        assertThat(s.waivedLateFee()).isEqualTo(1000);
        assertThat(s.totalDeductions()).isZero();
        assertThat(s.depositToReturn()).isEqualTo(1000);
        assertThat(s.finalAmount()).isEqualTo(1000);
    }
}
