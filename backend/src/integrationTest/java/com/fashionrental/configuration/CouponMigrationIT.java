package com.fashionrental.configuration;

import com.fashionrental.AbstractIntegrationTest;
import com.fashionrental.customer.Customer;
import com.fashionrental.customer.CustomerRepository;
import com.fashionrental.receipt.Receipt;
import com.fashionrental.receipt.ReceiptRepository;
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

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ReceiptRepository receiptRepository;

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

    @Test
    void receipt_with_a_discount_but_no_coupon_code_violates_the_check_constraint() {
        Receipt receipt = newReceiptWithDiscount(null, 50);

        assertThatThrownBy(() -> receiptRepository.saveAndFlush(receipt))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void receipt_with_a_zero_discount_and_no_coupon_code_is_allowed() {
        Receipt receipt = newReceiptWithDiscount(null, 0);

        assertThat(receiptRepository.saveAndFlush(receipt).getId()).isNotNull();
    }

    private Receipt newReceiptWithDiscount(String couponCode, int discountAmount) {
        Customer customer = new Customer();
        customer.setName("Test Customer");
        customer.setPhone("9800000000" + System.nanoTime() % 100);
        customer.setCustomerType(Customer.CustomerType.MISC);
        customer.setIsActive(true);
        customer = customerRepository.saveAndFlush(customer);

        OffsetDateTime start = OffsetDateTime.now();
        int totalRent = 300;
        int totalDeposit = 100;

        Receipt receipt = new Receipt();
        receipt.setReceiptNumber("R-TEST-" + System.nanoTime());
        receipt.setShareToken(String.valueOf(System.nanoTime()).substring(0, 12));
        receipt.setCustomer(customer);
        receipt.setStartDatetime(start);
        receipt.setEndDatetime(start.plusDays(3));
        receipt.setRentalDays(3);
        receipt.setTotalRent(totalRent);
        receipt.setTotalDeposit(totalDeposit);
        receipt.setCouponCode(couponCode);
        receipt.setDiscountAmount(discountAmount);
        receipt.setGrandTotal(totalRent - discountAmount + totalDeposit);
        return receipt;
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
