package com.fashionrental.receipt;

import com.fashionrental.AbstractIntegrationTest;
import com.fashionrental.configuration.Coupon;
import com.fashionrental.configuration.CouponRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CheckoutService#claimCouponUsage re-reads is_active to tell "deactivated mid-checkout"
 * apart from "usage limit reached". By the time it runs, CouponDiscountResolver has already
 * loaded the Coupon in the same transaction — so an entity lookup is served from the
 * persistence context and can only ever report the read-time flag, making the deactivation
 * branch unreachable. That is invisible to a mocked unit test, which is exactly how it
 * shipped once. This pins the read semantics the fix depends on.
 */
@Transactional
class CouponClaimStaleReadIT extends AbstractIntegrationTest {

    @Autowired private CouponRepository couponRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void only_the_scalar_projection_sees_a_deactivation_committed_after_the_entity_was_loaded() {
        UUID id = couponRepository.saveAndFlush(newCoupon("STALEREAD")).getId();
        entityManager.clear();

        // Stand in for CouponDiscountResolver: load the entity into this transaction's
        // persistence context while the coupon is still active.
        Coupon loaded = couponRepository.findByCode("STALEREAD").orElseThrow();
        assertThat(loaded.getIsActive()).isTrue();

        // The owner deactivates the coupon. A bulk UPDATE writes straight to the row and
        // leaves the managed copy untouched — the same shape as another transaction
        // committing a deactivation mid-checkout.
        entityManager.createQuery("UPDATE Coupon c SET c.isActive = false WHERE c.id = :id")
                .setParameter("id", id)
                .executeUpdate();

        assertThat(couponRepository.findById(id).orElseThrow().getIsActive())
                .as("findById is served from the persistence context and reports the stale flag")
                .isTrue();

        assertThat(couponRepository.findIsActiveById(id).orElseThrow())
                .as("the scalar projection cannot be cached and re-reads the row")
                .isFalse();
    }

    private Coupon newCoupon(String code) {
        Coupon coupon = new Coupon();
        coupon.setCode(code);
        coupon.setDiscountType(Coupon.DiscountType.PERCENT);
        coupon.setValue(10);
        coupon.setValidFrom(OffsetDateTime.now().minusDays(1));
        coupon.setValidTo(OffsetDateTime.now().plusDays(30));
        coupon.setIsActive(true);
        return coupon;
    }
}
