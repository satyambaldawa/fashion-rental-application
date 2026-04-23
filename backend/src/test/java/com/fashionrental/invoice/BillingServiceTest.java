package com.fashionrental.invoice;

import com.fashionrental.common.exception.ValidationException;
import com.fashionrental.common.util.DateTimeUtil;
import com.fashionrental.configuration.LateFeeRule;
import com.fashionrental.inventory.Item;
import com.fashionrental.invoice.model.request.ReturnLineItem;
import com.fashionrental.receipt.ReceiptLineItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BillingServiceTest {

    private BillingService billingService;

    private static final OffsetDateTime END = OffsetDateTime.of(2026, 4, 20, 10, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));

    @BeforeEach
    void setUp() {
        billingService = new BillingService(new DateTimeUtil());
    }

    // ── Late fee ─────────────────────────────────────────────────────────────

    @Test
    void should_return_zero_late_fee_when_returned_on_time() {
        OffsetDateTime returnTime = END;
        int fee = billingService.calculateLateFee(END, returnTime, 200, 1, defaultRules());
        assertThat(fee).isZero();
    }

    @Test
    void should_return_zero_late_fee_when_returned_early() {
        OffsetDateTime returnTime = END.minusHours(2);
        int fee = billingService.calculateLateFee(END, returnTime, 200, 1, defaultRules());
        assertThat(fee).isZero();
    }

    @Test
    void should_apply_0_5x_multiplier_for_2_hours_overdue() {
        // 2 hrs overdue → highest matching tier is durationFrom=0 (0.5x)
        OffsetDateTime returnTime = END.plusHours(2);
        int fee = billingService.calculateLateFee(END, returnTime, 200, 2, defaultRules());
        // 200 * 0.5 * 2 = 200
        assertThat(fee).isEqualTo(200);
    }

    @Test
    void should_apply_0_75x_multiplier_for_exactly_3_hours_overdue() {
        // At exactly 3hrs overdue, durationFrom=3 also qualifies → 0.75x wins
        OffsetDateTime returnTime = END.plusHours(3);
        int fee = billingService.calculateLateFee(END, returnTime, 200, 2, defaultRules());
        // 200 * 0.75 * 2 = 300
        assertThat(fee).isEqualTo(300);
    }

    @Test
    void should_apply_1_5x_multiplier_for_25_hours_overdue() {
        // 25 hrs overdue → tier 24-48hr (durationFrom=24) → 1.5x
        OffsetDateTime returnTime = END.plusHours(25);
        int fee = billingService.calculateLateFee(END, returnTime, 200, 1, defaultRules());
        // 200 * 1.5 * 1 = 300
        assertThat(fee).isEqualTo(300);
    }

    @Test
    void should_apply_2_0x_multiplier_for_49_hours_overdue() {
        // 49 hrs overdue → tier 48+ (durationFrom=48) → 2.0x
        OffsetDateTime returnTime = END.plusHours(49);
        int fee = billingService.calculateLateFee(END, returnTime, 200, 1, defaultRules());
        // 200 * 2.0 * 1 = 400
        assertThat(fee).isEqualTo(400);
    }

    @Test
    void should_apply_1_5x_fallback_when_no_rule_matches_overdue() {
        // 5 hrs overdue but empty rules → fallback 1.5x
        OffsetDateTime returnTime = END.plusHours(5);
        int fee = billingService.calculateLateFee(END, returnTime, 200, 1, List.of());
        assertThat(fee).isEqualTo(300);
    }

    // ── Damage cost ──────────────────────────────────────────────────────────

    @Test
    void should_return_zero_damage_cost_when_not_damaged() {
        ReceiptLineItem rli = lineItem(200, 3000);
        ReturnLineItem returnLine = new ReturnLineItem(UUID.randomUUID(), false, null, null);
        assertThat(billingService.calculateDamageCost(rli, returnLine)).isZero();
    }

    @Test
    void should_calculate_damage_cost_using_purchase_rate_and_percentage() {
        // purchase rate ₹3000, 30% damage → ₹900
        ReceiptLineItem rli = lineItem(200, 3000);
        ReturnLineItem returnLine = new ReturnLineItem(UUID.randomUUID(), true, 30.0, null);
        assertThat(billingService.calculateDamageCost(rli, returnLine)).isEqualTo(900);
    }

    @Test
    void should_throw_when_percentage_used_but_purchase_rate_is_null() {
        ReceiptLineItem rli = lineItem(200, null);
        ReturnLineItem returnLine = new ReturnLineItem(UUID.randomUUID(), true, 30.0, null);
        assertThatThrownBy(() -> billingService.calculateDamageCost(rli, returnLine))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Purchase cost not available");
    }

    @Test
    void should_calculate_damage_cost_by_ad_hoc_amount() {
        ReceiptLineItem rli = lineItem(200, null);  // no purchase rate needed for ad hoc
        ReturnLineItem returnLine = new ReturnLineItem(UUID.randomUUID(), true, null, 500);
        assertThat(billingService.calculateDamageCost(rli, returnLine)).isEqualTo(500);
    }

    @Test
    void should_prefer_ad_hoc_amount_over_percentage_when_both_provided() {
        ReceiptLineItem rli = lineItem(200, 3000);
        ReturnLineItem returnLine = new ReturnLineItem(UUID.randomUUID(), true, 30.0, 500);
        assertThat(billingService.calculateDamageCost(rli, returnLine)).isEqualTo(500);
    }

    @Test
    void should_return_zero_when_damaged_but_no_amount_specified() {
        ReceiptLineItem rli = lineItem(200, null);
        ReturnLineItem returnLine = new ReturnLineItem(UUID.randomUUID(), true, null, null);
        assertThat(billingService.calculateDamageCost(rli, returnLine)).isZero();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private ReceiptLineItem lineItem(int rateSnapshot, Integer purchaseRate) {
        Item item = new Item();
        item.setName("Test Item");
        item.setRate(rateSnapshot);
        item.setDeposit(1000);
        item.setQuantity(1);
        item.setPurchaseRate(purchaseRate);

        ReceiptLineItem rli = new ReceiptLineItem();
        rli.setItem(item);
        rli.setRateSnapshot(rateSnapshot);
        rli.setDepositSnapshot(1000);
        rli.setQuantity(1);
        return rli;
    }

    private List<LateFeeRule> defaultRules() {
        return List.of(
                rule(0, 0.50),
                rule(3, 0.75),
                rule(6, 1.00),
                rule(24, 1.50),
                rule(48, 2.00)
        );
    }

    private LateFeeRule rule(int durationFromHours, double multiplier) {
        LateFeeRule r = new LateFeeRule();
        r.setDurationFromHours(durationFromHours);
        r.setPenaltyMultiplier(BigDecimal.valueOf(multiplier));
        r.setIsActive(true);
        r.setSortOrder(durationFromHours);
        return r;
    }
}
