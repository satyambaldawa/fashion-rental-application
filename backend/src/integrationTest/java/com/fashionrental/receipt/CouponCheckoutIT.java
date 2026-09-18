package com.fashionrental.receipt;

import com.fashionrental.AbstractIntegrationTest;
import com.fashionrental.configuration.Coupon;
import com.fashionrental.configuration.CouponRepository;
import com.fashionrental.customer.Customer;
import com.fashionrental.customer.CustomerRepository;
import com.fashionrental.inventory.Item;
import com.fashionrental.inventory.ItemRepository;
import com.fashionrental.receipt.model.request.CheckoutLineItem;
import com.fashionrental.receipt.model.request.CheckoutRequest;
import com.fashionrental.receipt.model.response.ReceiptResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full checkout against real PostgreSQL with a valid coupon — proves the discount
 * persists correctly, the deposit is genuinely untouched, and the grand_total identity
 * (and the receipts_grand_total_check constraint backing it) holds.
 */
@Transactional
class CouponCheckoutIT extends AbstractIntegrationTest {

    @Autowired private CheckoutService checkoutService;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private ItemRepository itemRepository;
    @Autowired private CouponRepository couponRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @BeforeEach
    void authenticateAsOwner() {
        authenticateAs("ROLE_OWNER");
    }

    private void authenticateAs(String role) {
        var auth = new UsernamePasswordAuthenticationToken(
                "staff", null, List.of(new SimpleGrantedAuthority(role)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void checkout_with_a_valid_coupon_persists_the_discount_and_leaves_the_deposit_untouched() {
        Customer customer = newCustomer("Deepa", "9822200011");
        Item item = newItem("Silk Saree", 300, 1500);

        Coupon coupon = new Coupon();
        coupon.setCode("WELCOME10");
        coupon.setDiscountType(Coupon.DiscountType.PERCENT);
        coupon.setValue(10);
        coupon.setValidFrom(OffsetDateTime.now().minusDays(1));
        coupon.setValidTo(OffsetDateTime.now().plusDays(1));
        coupon.setIsActive(true);
        couponRepository.save(coupon);

        OffsetDateTime start = OffsetDateTime.parse("2026-05-10T10:00:00+05:30");
        OffsetDateTime end = OffsetDateTime.parse("2026-05-12T10:00:00+05:30");

        ReceiptResponse receipt = checkoutService.createReceipt(new CheckoutRequest(
                customer.getId(), start, end,
                List.of(new CheckoutLineItem(item.getId(), 1)),
                List.of(), null, "welcome10" // lower-case on purpose: proves normalisation
        ));

        // rent = 300 * 2 days * 1 = 600; 10% discount = 60; deposit untouched at 1500
        assertThat(receipt.couponCode()).isEqualTo("WELCOME10");
        assertThat(receipt.discountAmount()).isEqualTo(60);
        assertThat(receipt.totalRent()).isEqualTo(600);
        assertThat(receipt.totalDeposit()).isEqualTo(1500);
        assertThat(receipt.grandTotal()).isEqualTo(600 - 60 + 1500);

        // incrementTimesUsed is a @Modifying bulk UPDATE — it writes straight to the
        // database and bypasses the persistence context, so the `coupon` instance
        // already held in this transaction's first-level cache is now stale. Clear it
        // to force a real reload rather than silently reading the cached pre-update copy.
        entityManager.clear();
        Coupon reloaded = couponRepository.findById(coupon.getId()).orElseThrow();
        assertThat(reloaded.getTimesUsed()).isEqualTo(1);
    }

    @Test
    void checkout_without_a_coupon_code_persists_a_zero_discount_and_a_null_coupon_code() {
        Customer customer = newCustomer("Naveen", "9822200022");
        Item item = newItem("Cotton Kurta", 100, 300);

        OffsetDateTime start = OffsetDateTime.parse("2026-05-10T10:00:00+05:30");
        OffsetDateTime end = OffsetDateTime.parse("2026-05-11T10:00:00+05:30");

        ReceiptResponse receipt = checkoutService.createReceipt(new CheckoutRequest(
                customer.getId(), start, end,
                List.of(new CheckoutLineItem(item.getId(), 1)),
                List.of(), null, null
        ));

        assertThat(receipt.couponCode()).isNull();
        assertThat(receipt.discountAmount()).isZero();
        assertThat(receipt.grandTotal()).isEqualTo(receipt.totalRent() + receipt.totalDeposit());
    }

    @Test
    void checkout_rejects_an_expired_coupon_with_a_clear_message() {
        Customer customer = newCustomer("Ritu", "9822200033");
        Item item = newItem("Velvet Blazer", 250, 800);

        Coupon expired = new Coupon();
        expired.setCode("OLD2025");
        expired.setDiscountType(Coupon.DiscountType.FIXED);
        expired.setValue(50);
        expired.setValidFrom(OffsetDateTime.now().minusDays(30));
        expired.setValidTo(OffsetDateTime.now().minusDays(1));
        expired.setIsActive(true);
        couponRepository.save(expired);

        OffsetDateTime start = OffsetDateTime.parse("2026-05-10T10:00:00+05:30");
        OffsetDateTime end = OffsetDateTime.parse("2026-05-11T10:00:00+05:30");

        CheckoutRequest request = new CheckoutRequest(
                customer.getId(), start, end,
                List.of(new CheckoutLineItem(item.getId(), 1)),
                List.of(), null, "OLD2025"
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> checkoutService.createReceipt(request))
                .isInstanceOf(com.fashionrental.common.exception.ValidationException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void executive_can_apply_a_coupon_at_checkout() {
        // Managing coupons is OWNER-only (/api/config/**); *applying* one is ordinary
        // counter work. That asymmetry falls out of SecurityConfig's matchers rather than
        // any explicit check, so it is asserted rather than assumed.
        authenticateAs("ROLE_EXECUTIVE");

        Customer customer = newCustomer("Farah", "9822200044");
        Item item = newItem("Anarkali Gown", 400, 1000);

        Coupon coupon = new Coupon();
        coupon.setCode("STAFF25");
        coupon.setDiscountType(Coupon.DiscountType.PERCENT);
        coupon.setValue(25);
        coupon.setValidFrom(OffsetDateTime.now().minusDays(1));
        coupon.setValidTo(OffsetDateTime.now().plusDays(1));
        coupon.setIsActive(true);
        couponRepository.save(coupon);

        OffsetDateTime start = OffsetDateTime.parse("2026-05-10T10:00:00+05:30");
        OffsetDateTime end = OffsetDateTime.parse("2026-05-12T10:00:00+05:30");

        ReceiptResponse receipt = checkoutService.createReceipt(new CheckoutRequest(
                customer.getId(), start, end,
                List.of(new CheckoutLineItem(item.getId(), 1)),
                List.of(), null, "STAFF25"
        ));

        // rent = 400 * 2 days = 800; 25% = 200
        assertThat(receipt.couponCode()).isEqualTo("STAFF25");
        assertThat(receipt.discountAmount()).isEqualTo(200);
        assertThat(receipt.grandTotal()).isEqualTo(800 - 200 + 1000);
    }

    private Customer newCustomer(String name, String phone) {
        Customer customer = new Customer();
        customer.setName(name);
        customer.setPhone(phone);
        customer.setCustomerType(Customer.CustomerType.MISC);
        customer.setIsActive(true);
        return customerRepository.save(customer);
    }

    private Item newItem(String name, int rate, int deposit) {
        Item item = new Item();
        item.setName(name);
        item.setCategory(Item.Category.COSTUME);
        item.setItemType(Item.ItemType.INDIVIDUAL);
        item.setRate(rate);
        item.setDeposit(deposit);
        item.setQuantity(3);
        item.setIsActive(true);
        return itemRepository.save(item);
    }
}
