package com.fashionrental.configuration;

import com.fashionrental.AbstractIntegrationTest;
import com.fashionrental.configuration.model.SetCouponStatusRequest;
import com.fashionrental.configuration.model.UpdateCouponRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * times_used is written by the checkout path and never appears on an admin request DTO —
 * but Hibernate's default flush writes *every* column, so an admin edit would still push
 * the entity's loaded times_used back over any increment committed in between. @DynamicUpdate
 * on Coupon is the only thing preventing that, and it is an annotation someone could remove
 * while every unit test stayed green. These tests execute the real UPDATE statement.
 */
@Transactional
class CouponAdminDoesNotClobberTimesUsedIT extends AbstractIntegrationTest {

    @Autowired private CouponRepository couponRepository;
    @Autowired private CouponService couponService;

    @PersistenceContext
    private EntityManager entityManager;

    private static final OffsetDateTime FROM = OffsetDateTime.parse("2026-06-01T00:00:00+05:30");
    private static final OffsetDateTime TO = OffsetDateTime.parse("2026-06-30T00:00:00+05:30");

    @Test
    void editing_a_coupon_does_not_write_back_a_stale_times_used() {
        UUID id = couponRepository.saveAndFlush(newCoupon("EDITSAFE")).getId();

        // A checkout claims a usage. The bulk UPDATE writes straight to the row, so the
        // entity this transaction already holds keeps times_used = 0 — precisely the stale
        // copy an admin edit would flush back.
        int claimed = couponRepository.incrementTimesUsed(id);
        assertThat(claimed).isEqualTo(1);

        couponService.updateCoupon(id, new UpdateCouponRequest("PERCENT", 25, null, FROM, TO, null));
        entityManager.flush();
        entityManager.clear();

        Coupon reloaded = couponRepository.findById(id).orElseThrow();
        assertThat(reloaded.getValue()).isEqualTo(25);
        assertThat(reloaded.getTimesUsed()).isEqualTo(1);
    }

    @Test
    void deactivating_a_coupon_does_not_write_back_a_stale_times_used() {
        UUID id = couponRepository.saveAndFlush(newCoupon("STOPSAFE")).getId();

        int claimed = couponRepository.incrementTimesUsed(id);
        assertThat(claimed).isEqualTo(1);

        couponService.setStatus(id, new SetCouponStatusRequest(false));
        entityManager.flush();
        entityManager.clear();

        Coupon reloaded = couponRepository.findById(id).orElseThrow();
        assertThat(reloaded.getIsActive()).isFalse();
        assertThat(reloaded.getTimesUsed()).isEqualTo(1);
    }

    private Coupon newCoupon(String code) {
        Coupon coupon = new Coupon();
        coupon.setCode(code);
        coupon.setDiscountType(Coupon.DiscountType.PERCENT);
        coupon.setValue(10);
        coupon.setValidFrom(FROM);
        coupon.setValidTo(TO);
        coupon.setIsActive(true);
        return coupon;
    }
}
