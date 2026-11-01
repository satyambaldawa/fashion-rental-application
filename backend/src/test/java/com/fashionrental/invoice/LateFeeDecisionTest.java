package com.fashionrental.invoice;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LateFeeDecisionTest {

    @Test
    void should_apply_when_flag_is_null() {
        assertThat(LateFeeDecision.from(null)).isEqualTo(LateFeeDecision.APPLY);
    }

    @Test
    void should_apply_when_flag_is_true() {
        assertThat(LateFeeDecision.from(Boolean.TRUE)).isEqualTo(LateFeeDecision.APPLY);
    }

    @Test
    void should_waive_when_flag_is_false() {
        assertThat(LateFeeDecision.from(Boolean.FALSE)).isEqualTo(LateFeeDecision.WAIVE);
    }
}
