package com.fashionrental.receipt;

import com.fashionrental.common.exception.ValidationException;
import com.fashionrental.configuration.Coupon;
import com.fashionrental.configuration.CouponRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponDiscountResolverTest {

    @Mock CouponRepository couponRepository;

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-06-15T12:00:00+05:30");
    private final Clock clock = Clock.fixed(NOW.toInstant(), ZoneOffset.of("+05:30"));

    private CouponDiscountResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new CouponDiscountResolver(couponRepository, clock);
    }

    // ─── resolve() — presence / lookup ─────────────────────────────────────

    @Test
    void should_return_no_discount_when_code_is_null() {
        AppliedDiscount discount = resolver.resolve(null, new DiscountableSubtotals(1000, 0));

        assertThat(discount.isApplied()).isFalse();
        assertThat(discount.amount()).isZero();
    }

    @Test
    void should_return_no_discount_when_code_is_blank() {
        AppliedDiscount discount = resolver.resolve("", new DiscountableSubtotals(1000, 0));

        assertThat(discount.isApplied()).isFalse();
    }

    @Test
    void should_return_no_discount_when_code_is_whitespace_only() {
        AppliedDiscount discount = resolver.resolve("   ", new DiscountableSubtotals(1000, 0));

        assertThat(discount.isApplied()).isFalse();
    }

    @Test
    void should_match_code_after_trimming_and_uppercasing() {
        Coupon coupon = validCoupon("SAVE20", Coupon.DiscountType.PERCENT, 20, null, null);
        when(couponRepository.findByCode("SAVE20")).thenReturn(Optional.of(coupon));

        AppliedDiscount discount = resolver.resolve("  save20  ", new DiscountableSubtotals(1000, 0));

        assertThat(discount.code()).isEqualTo("SAVE20");
    }

    @Test
    void should_reject_when_code_is_not_found() {
        when(couponRepository.findByCode("MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolve("MISSING", new DiscountableSubtotals(1000, 0)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("is not valid");
    }

    @Test
    void should_reject_when_coupon_is_inactive() {
        Coupon coupon = validCoupon("SAVE20", Coupon.DiscountType.PERCENT, 20, null, null);
        coupon.setIsActive(false);
        when(couponRepository.findByCode("SAVE20")).thenReturn(Optional.of(coupon));

        assertThatThrownBy(() -> resolver.resolve("SAVE20", new DiscountableSubtotals(1000, 0)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("no longer active");
    }

    @Test
    void should_reject_when_now_is_before_valid_from() {
        Coupon coupon = validCoupon("SAVE20", Coupon.DiscountType.PERCENT, 20, null, null);
        coupon.setValidFrom(NOW.plusDays(1));
        when(couponRepository.findByCode("SAVE20")).thenReturn(Optional.of(coupon));

        assertThatThrownBy(() -> resolver.resolve("SAVE20", new DiscountableSubtotals(1000, 0)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("not valid yet");
    }

    @Test
    void should_reject_when_now_is_after_valid_to() {
        Coupon coupon = validCoupon("SAVE20", Coupon.DiscountType.PERCENT, 20, null, null);
        coupon.setValidTo(NOW.minusDays(1));
        when(couponRepository.findByCode("SAVE20")).thenReturn(Optional.of(coupon));

        assertThatThrownBy(() -> resolver.resolve("SAVE20", new DiscountableSubtotals(1000, 0)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void should_accept_when_now_is_exactly_valid_from() {
        Coupon coupon = validCoupon("SAVE20", Coupon.DiscountType.PERCENT, 20, null, null);
        coupon.setValidFrom(NOW);
        when(couponRepository.findByCode("SAVE20")).thenReturn(Optional.of(coupon));

        AppliedDiscount discount = resolver.resolve("SAVE20", new DiscountableSubtotals(1000, 0));

        assertThat(discount.isApplied()).isTrue();
    }

    @Test
    void should_reject_when_times_used_has_reached_usage_limit() {
        Coupon coupon = validCoupon("SAVE20", Coupon.DiscountType.PERCENT, 20, 5, null);
        coupon.setTimesUsed(5);
        when(couponRepository.findByCode("SAVE20")).thenReturn(Optional.of(coupon));

        assertThatThrownBy(() -> resolver.resolve("SAVE20", new DiscountableSubtotals(1000, 0)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("usage limit");
    }

    @Test
    void should_accept_when_usage_limit_is_null() {
        Coupon coupon = validCoupon("SAVE20", Coupon.DiscountType.PERCENT, 20, null, null);
        coupon.setTimesUsed(1000); // arbitrarily high — no limit means no rejection
        when(couponRepository.findByCode("SAVE20")).thenReturn(Optional.of(coupon));

        AppliedDiscount discount = resolver.resolve("SAVE20", new DiscountableSubtotals(1000, 0));

        assertThat(discount.isApplied()).isTrue();
    }

    @Test
    void should_reject_when_subtotal_is_below_min_subtotal() {
        Coupon coupon = validCoupon("SAVE20", Coupon.DiscountType.PERCENT, 20, null, 2000);
        when(couponRepository.findByCode("SAVE20")).thenReturn(Optional.of(coupon));

        assertThatThrownBy(() -> resolver.resolve("SAVE20", new DiscountableSubtotals(1999, 0)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("minimum rent subtotal");
    }

    @Test
    void should_name_the_sale_bucket_in_the_minimum_subtotal_message_when_sale_is_populated() {
        // Guards the #57 seam: the wording is derived from DiscountableSubtotals, so it stays
        // truthful the day the sale bucket starts carrying a value instead of quietly telling
        // staff the order's "rent subtotal" is a number that includes sale lines.
        Coupon coupon = validCoupon("SAVE20", Coupon.DiscountType.PERCENT, 20, null, 2000);
        when(couponRepository.findByCode("SAVE20")).thenReturn(Optional.of(coupon));

        assertThatThrownBy(() -> resolver.resolve("SAVE20", new DiscountableSubtotals(900, 600)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("rent and sale subtotal")
                .hasMessageContaining("₹1500");
    }

    @Test
    void should_accept_when_subtotal_equals_min_subtotal() {
        Coupon coupon = validCoupon("SAVE20", Coupon.DiscountType.PERCENT, 20, null, 2000);
        when(couponRepository.findByCode("SAVE20")).thenReturn(Optional.of(coupon));

        AppliedDiscount discount = resolver.resolve("SAVE20", new DiscountableSubtotals(2000, 0));

        assertThat(discount.isApplied()).isTrue();
    }

    @Test
    void should_report_the_first_failing_condition_when_several_fail() {
        // Both "inactive" and "below min subtotal" are true; isActive is checked first.
        Coupon coupon = validCoupon("SAVE20", Coupon.DiscountType.PERCENT, 20, null, 2000);
        coupon.setIsActive(false);
        when(couponRepository.findByCode("SAVE20")).thenReturn(Optional.of(coupon));

        assertThatThrownBy(() -> resolver.resolve("SAVE20", new DiscountableSubtotals(100, 0)))
                .hasMessageContaining("no longer active");
    }

    // ─── computeDiscountAmount() — pure arithmetic ─────────────────────────

    @Test
    void should_floor_percent_discount_to_whole_rupee() {
        Coupon coupon = validCoupon("SAVE10", Coupon.DiscountType.PERCENT, 10, null, null);

        assertThat(CouponDiscountResolver.computeDiscountAmount(coupon, 1505)).isEqualTo(150);
    }

    @Test
    void should_yield_zero_when_percent_discount_floors_below_one_rupee() {
        Coupon coupon = validCoupon("SAVE1", Coupon.DiscountType.PERCENT, 1, null, null);

        assertThat(CouponDiscountResolver.computeDiscountAmount(coupon, 50)).isZero();
    }

    @Test
    void should_apply_full_subtotal_for_a_hundred_percent_coupon() {
        Coupon coupon = validCoupon("FREE", Coupon.DiscountType.PERCENT, 100, null, null);

        assertThat(CouponDiscountResolver.computeDiscountAmount(coupon, 750)).isEqualTo(750);
    }

    @Test
    void should_cap_fixed_discount_at_the_discountable_subtotal() {
        Coupon coupon = validCoupon("FLAT500", Coupon.DiscountType.FIXED, 500, null, null);

        assertThat(CouponDiscountResolver.computeDiscountAmount(coupon, 300)).isEqualTo(300);
    }

    @Test
    void should_never_return_a_negative_discount() {
        Coupon coupon = validCoupon("FLAT500", Coupon.DiscountType.FIXED, 500, null, null);

        assertThat(CouponDiscountResolver.computeDiscountAmount(coupon, 0)).isGreaterThanOrEqualTo(0);
    }

    @Test
    void should_return_zero_when_the_discountable_subtotal_is_zero() {
        Coupon coupon = validCoupon("SAVE20", Coupon.DiscountType.PERCENT, 20, null, null);

        assertThat(CouponDiscountResolver.computeDiscountAmount(coupon, 0)).isZero();
    }

    @Test
    void should_include_the_sale_subtotal_in_the_base() {
        Coupon coupon = validCoupon("SAVE10", Coupon.DiscountType.PERCENT, 10, null, null);
        when(couponRepository.findByCode("SAVE10")).thenReturn(Optional.of(coupon));

        AppliedDiscount discount = resolver.resolve("SAVE10", new DiscountableSubtotals(100, 50));

        assertThat(discount.amount()).isEqualTo(15); // 10% of (100 + 50)
    }

    private Coupon validCoupon(String code, Coupon.DiscountType type, int value, Integer usageLimit, Integer minSubtotal) {
        Coupon coupon = new Coupon();
        coupon.setCode(code);
        coupon.setDiscountType(type);
        coupon.setValue(value);
        coupon.setValidFrom(NOW.minusDays(30));
        coupon.setValidTo(NOW.plusDays(30));
        coupon.setUsageLimit(usageLimit);
        coupon.setMinSubtotal(minSubtotal);
        coupon.setTimesUsed(0);
        coupon.setIsActive(true);
        return coupon;
    }
}
