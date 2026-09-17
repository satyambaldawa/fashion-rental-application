package com.fashionrental.configuration;

import com.fashionrental.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Boots the coupons migration against real PostgreSQL and confirms the unique code
 * index is enforced — a mock-based test never executes the actual constraint.
 */
@Transactional
class CouponMigrationIT extends AbstractIntegrationTest {

    @Autowired
    private CouponRepository couponRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void duplicate_code_violates_the_unique_index() {
        couponRepository.saveAndFlush(newCoupon("SAVE20"));

        assertThatThrownBy(() -> couponRepository.saveAndFlush(newCoupon("SAVE20")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void entity_persists_and_reloads_with_all_fields() {
        Coupon saved = couponRepository.saveAndFlush(newCoupon("WELCOME10"));
        entityManager.clear();

        Coupon loaded = couponRepository.findById(saved.getId()).orElseThrow();

        assertThat(loaded.getCode()).isEqualTo("WELCOME10");
        assertThat(loaded.getDiscountType()).isEqualTo(Coupon.DiscountType.PERCENT);
        assertThat(loaded.getValue()).isEqualTo(10);
        assertThat(loaded.getTimesUsed()).isZero();
        assertThat(loaded.getIsActive()).isTrue();
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isNotNull();
    }

    private Coupon newCoupon(String code) {
        Coupon coupon = new Coupon();
        coupon.setCode(code);
        coupon.setDiscountType(Coupon.DiscountType.PERCENT);
        coupon.setValue(10);
        coupon.setValidFrom(OffsetDateTime.now().minusDays(1));
        coupon.setValidTo(OffsetDateTime.now().plusDays(30));
        return coupon;
    }
}
