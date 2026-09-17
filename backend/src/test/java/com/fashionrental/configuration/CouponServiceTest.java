package com.fashionrental.configuration;

import com.fashionrental.common.exception.ConflictException;
import com.fashionrental.common.exception.ResourceNotFoundException;
import com.fashionrental.common.exception.ValidationException;
import com.fashionrental.configuration.model.CreateCouponRequest;
import com.fashionrental.configuration.model.CouponResponse;
import com.fashionrental.configuration.model.SetCouponStatusRequest;
import com.fashionrental.configuration.model.UpdateCouponRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponServiceTest {

    @Mock CouponRepository couponRepository;

    @InjectMocks CouponService couponService;

    private static final OffsetDateTime FROM = OffsetDateTime.parse("2026-06-01T00:00:00+05:30");
    private static final OffsetDateTime TO = OffsetDateTime.parse("2026-06-30T23:59:59+05:30");

    @Test
    void should_reject_percent_value_above_one_hundred() {
        CreateCouponRequest request = new CreateCouponRequest(
                "SAVE200", "PERCENT", 200, null, FROM, TO, null);

        assertThatThrownBy(() -> couponService.createCoupon(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("between 1 and 100");
    }

    @Test
    void should_reject_percent_value_below_one() {
        CreateCouponRequest request = new CreateCouponRequest(
                "SAVE0", "PERCENT", 0, null, FROM, TO, null);

        assertThatThrownBy(() -> couponService.createCoupon(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("between 1 and 100");
    }

    @Test
    void should_reject_valid_to_not_after_valid_from() {
        CreateCouponRequest request = new CreateCouponRequest(
                "SAVE20", "PERCENT", 20, null, TO, FROM, null);

        assertThatThrownBy(() -> couponService.createCoupon(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("must be after its start");
    }

    @Test
    void should_reject_an_unrecognised_discount_type_as_a_validation_error_not_a_server_error() {
        CreateCouponRequest request = new CreateCouponRequest(
                "SAVE20", "PERCENTAGE", 20, null, FROM, TO, null);

        assertThatThrownBy(() -> couponService.createCoupon(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("PERCENT or FIXED");
    }

    @Test
    void should_reject_duplicate_code_ignoring_case_and_whitespace() {
        when(couponRepository.existsByCode("SAVE20")).thenReturn(true);
        CreateCouponRequest request = new CreateCouponRequest(
                "  save20  ", "PERCENT", 20, null, FROM, TO, null);

        assertThatThrownBy(() -> couponService.createCoupon(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("SAVE20");
    }

    @Test
    void should_normalise_code_to_uppercase_on_create() {
        when(couponRepository.existsByCode("SAVE20")).thenReturn(false);
        when(couponRepository.save(any(Coupon.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateCouponRequest request = new CreateCouponRequest(
                "  save20  ", "PERCENT", 20, null, FROM, TO, null);

        ArgumentCaptor<Coupon> captor = ArgumentCaptor.forClass(Coupon.class);
        couponService.createCoupon(request);

        org.mockito.Mockito.verify(couponRepository).save(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo("SAVE20");
    }

    @Test
    void should_normalise_valid_to_to_end_of_the_ist_day_on_create() {
        when(couponRepository.existsByCode("SAVE20")).thenReturn(false);
        when(couponRepository.save(any(Coupon.class))).thenAnswer(inv -> inv.getArgument(0));

        // A date picker sends midnight; without normalisation the coupon dies before its
        // last day trades at all.
        OffsetDateTime midnightOnTheLastDay = OffsetDateTime.parse("2026-06-30T00:00:00+05:30");
        CreateCouponRequest request = new CreateCouponRequest(
                "SAVE20", "PERCENT", 20, null, FROM, midnightOnTheLastDay, null);

        ArgumentCaptor<Coupon> captor = ArgumentCaptor.forClass(Coupon.class);
        couponService.createCoupon(request);

        org.mockito.Mockito.verify(couponRepository).save(captor.capture());
        assertThat(captor.getValue().getValidTo())
                .isEqualTo(OffsetDateTime.parse("2026-06-30T23:59:59+05:30"));
    }

    @Test
    void should_normalise_valid_to_to_end_of_the_ist_day_on_update() {
        UUID id = UUID.randomUUID();
        Coupon existing = existingCoupon(id, "SAVE20", 10);

        when(couponRepository.findById(id)).thenReturn(Optional.of(existing));
        when(couponRepository.save(any(Coupon.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateCouponRequest request = new UpdateCouponRequest(
                "PERCENT", 10, null, FROM, OffsetDateTime.parse("2026-07-15T09:30:00+05:30"), null);

        CouponResponse response = couponService.updateCoupon(id, request);

        assertThat(response.validTo()).isEqualTo(OffsetDateTime.parse("2026-07-15T23:59:59+05:30"));
    }

    @Test
    void should_normalise_valid_to_against_the_ist_calendar_day_not_the_submitted_offset() {
        when(couponRepository.existsByCode("SAVE20")).thenReturn(false);
        when(couponRepository.save(any(Coupon.class))).thenAnswer(inv -> inv.getArgument(0));

        // 2026-06-30T20:00Z is 2026-07-01T01:30 IST — the IST day is what the shop means.
        CreateCouponRequest request = new CreateCouponRequest(
                "SAVE20", "PERCENT", 20, null, FROM,
                OffsetDateTime.parse("2026-06-30T20:00:00Z"), null);

        ArgumentCaptor<Coupon> captor = ArgumentCaptor.forClass(Coupon.class);
        couponService.createCoupon(request);

        org.mockito.Mockito.verify(couponRepository).save(captor.capture());
        assertThat(captor.getValue().getValidTo())
                .isEqualTo(OffsetDateTime.parse("2026-07-01T23:59:59+05:30"));
    }

    @Test
    void should_accept_a_single_day_validity_window() {
        when(couponRepository.existsByCode("ONEDAY")).thenReturn(false);
        when(couponRepository.save(any(Coupon.class))).thenAnswer(inv -> inv.getArgument(0));

        // Same date for both ends is a legal one-day promotion: end-of-day normalisation is
        // what makes validTo land after validFrom rather than equal to it.
        OffsetDateTime sameDay = OffsetDateTime.parse("2026-06-15T00:00:00+05:30");
        CreateCouponRequest request = new CreateCouponRequest(
                "ONEDAY", "FIXED", 100, null, sameDay, sameDay, null);

        CouponResponse response = couponService.createCoupon(request);

        assertThat(response.validFrom()).isEqualTo(sameDay);
        assertThat(response.validTo()).isEqualTo(OffsetDateTime.parse("2026-06-15T23:59:59+05:30"));
    }

    @Test
    void should_allow_lowering_usage_limit_below_times_used() {
        UUID id = UUID.randomUUID();
        Coupon existing = existingCoupon(id, "SAVE20", 10);
        existing.setUsageLimit(50);
        existing.setTimesUsed(20);

        when(couponRepository.findById(id)).thenReturn(Optional.of(existing));
        when(couponRepository.save(any(Coupon.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateCouponRequest request = new UpdateCouponRequest("PERCENT", 10, null, FROM, TO, 5);

        CouponResponse response = couponService.updateCoupon(id, request);

        assertThat(response.usageLimit()).isEqualTo(5);
        assertThat(response.timesUsed()).isEqualTo(20); // untouched — still above the new limit
    }

    @Test
    void should_not_expose_times_used_on_the_update_request() {
        UUID id = UUID.randomUUID();
        Coupon existing = existingCoupon(id, "SAVE20", 10);
        existing.setTimesUsed(7);

        when(couponRepository.findById(id)).thenReturn(Optional.of(existing));
        when(couponRepository.save(any(Coupon.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateCouponRequest request = new UpdateCouponRequest("PERCENT", 15, null, FROM, TO, null);

        CouponResponse response = couponService.updateCoupon(id, request);

        // UpdateCouponRequest has no timesUsed field to accept — this proves the value
        // survives an update untouched, which is the point.
        assertThat(response.timesUsed()).isEqualTo(7);
        assertThat(response.value()).isEqualTo(15);
    }

    @Test
    void should_throw_not_found_when_updating_a_missing_coupon() {
        UUID id = UUID.randomUUID();
        when(couponRepository.findById(id)).thenReturn(Optional.empty());

        UpdateCouponRequest request = new UpdateCouponRequest("PERCENT", 10, null, FROM, TO, null);

        assertThatThrownBy(() -> couponService.updateCoupon(id, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void should_deactivate_and_reactivate_via_set_status() {
        UUID id = UUID.randomUUID();
        Coupon existing = existingCoupon(id, "SAVE20", 10);

        when(couponRepository.findById(id)).thenReturn(Optional.of(existing));
        when(couponRepository.save(any(Coupon.class))).thenAnswer(inv -> inv.getArgument(0));

        CouponResponse deactivated = couponService.setStatus(id, new SetCouponStatusRequest(false));
        assertThat(deactivated.isActive()).isFalse();

        CouponResponse reactivated = couponService.setStatus(id, new SetCouponStatusRequest(true));
        assertThat(reactivated.isActive()).isTrue();
    }

    private Coupon existingCoupon(UUID id, String code, int value) {
        Coupon coupon = new Coupon();
        coupon.setCode(code);
        coupon.setDiscountType(Coupon.DiscountType.PERCENT);
        coupon.setValue(value);
        coupon.setValidFrom(FROM);
        coupon.setValidTo(TO);
        coupon.setIsActive(true);
        try {
            var field = Coupon.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(coupon, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return coupon;
    }
}
