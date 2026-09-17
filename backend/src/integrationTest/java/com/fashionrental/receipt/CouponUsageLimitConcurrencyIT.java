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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * Proves CouponRepository#incrementTimesUsed's conditional UPDATE actually prevents a
 * coupon's last usage slot from being claimed twice by real concurrent transactions —
 * the one property a mocked unit test cannot exercise.
 *
 * <p>Deliberately NOT @Transactional: a test-managed transaction would serialize the two
 * checkout calls onto one connection and defeat the entire point of this test.
 */
class CouponUsageLimitConcurrencyIT extends AbstractIntegrationTest {

    @Autowired private CheckoutService checkoutService;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private ItemRepository itemRepository;
    @Autowired private CouponRepository couponRepository;
    @Autowired private ReceiptRepository receiptRepository;

    @MockitoSpyBean private CouponDiscountResolver couponDiscountResolver;

    @AfterEach
    void cleanUp() {
        receiptRepository.deleteAll();
        couponRepository.deleteAll();
        itemRepository.deleteAll();
        customerRepository.deleteAll();
    }

    @Test
    void only_one_of_two_concurrent_checkouts_claims_the_last_usage_slot() throws Exception {
        Customer customerA = newCustomer("Rekha", "9811100011");
        Customer customerB = newCustomer("Suresh", "9811100022");

        Item item = new Item();
        item.setName("Party Gown");
        item.setCategory(Item.Category.COSTUME);
        item.setItemType(Item.ItemType.INDIVIDUAL);
        item.setRate(200);
        item.setDeposit(500);
        // >= 2: both concurrent carts must clear the availability guard, so the only
        // thing that can reject either of them is the coupon claim, not stock.
        item.setQuantity(4);
        item.setIsActive(true);
        item = itemRepository.save(item);

        Coupon coupon = new Coupon();
        coupon.setCode("LASTSLOT");
        coupon.setDiscountType(Coupon.DiscountType.PERCENT);
        coupon.setValue(10);
        coupon.setValidFrom(OffsetDateTime.now().minusDays(1));
        coupon.setValidTo(OffsetDateTime.now().plusDays(1));
        coupon.setUsageLimit(1);
        coupon.setTimesUsed(0);
        coupon.setIsActive(true);
        coupon = couponRepository.save(coupon);

        OffsetDateTime start = OffsetDateTime.parse("2026-05-01T10:00:00+05:30");
        OffsetDateTime end = OffsetDateTime.parse("2026-05-03T10:00:00+05:30");

        // Both worker threads count down and then await the same latch, so each blocks
        // immediately after CouponDiscountResolver#resolve (the read-time pre-check,
        // which has already seen times_used = 0, under the limit) and before
        // CheckoutService#claimCouponUsage (the atomic UPDATE). The latch releases both
        // simultaneously only once both have arrived, forcing them into the database at
        // the same time — the DB-level guard, not the read-time pre-check, is what has
        // to prevent the double claim.
        CountDownLatch bothResolved = new CountDownLatch(2);
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            bothResolved.countDown();
            if (!bothResolved.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Test barrier timed out — the other thread never arrived");
            }
            return result;
        }).when(couponDiscountResolver).resolve(any(), any());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        Item finalItem = item;

        Future<Outcome> futureA = pool.submit(() -> attemptCheckout(customerA, finalItem, start, end));
        Future<Outcome> futureB = pool.submit(() -> attemptCheckout(customerB, finalItem, start, end));

        Outcome outcomeA = futureA.get(15, TimeUnit.SECONDS);
        Outcome outcomeB = futureB.get(15, TimeUnit.SECONDS);
        pool.shutdown();

        List<Outcome> outcomes = List.of(outcomeA, outcomeB);
        long succeeded = outcomes.stream().filter(o -> o.succeeded).count();
        long rejectedOnUsageLimit = outcomes.stream()
                .filter(o -> !o.succeeded && o.failureMessage != null && o.failureMessage.contains("usage limit"))
                .count();

        assertThat(succeeded).as("exactly one checkout should have claimed the slot").isEqualTo(1);
        assertThat(rejectedOnUsageLimit)
                .as("the loser must be rejected for the usage limit, not availability")
                .isEqualTo(1);
        assertThat(couponRepository.findById(coupon.getId()).orElseThrow().getTimesUsed()).isEqualTo(1);
        assertThat(receiptRepository.count()).isEqualTo(1);
    }

    private Outcome attemptCheckout(Customer customer, Item item, OffsetDateTime start, OffsetDateTime end) {
        // SecurityContextHolder is thread-local and never inherited from the test's main
        // thread. This fixture has no ad-hoc items, so hasOwnerRole() is never consulted,
        // but set it anyway so this test doesn't become a trap for a future edit.
        var auth = new UsernamePasswordAuthenticationToken(
                "owner", null, List.of(new SimpleGrantedAuthority("ROLE_OWNER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            CheckoutRequest request = new CheckoutRequest(
                    customer.getId(), start, end,
                    List.of(new CheckoutLineItem(item.getId(), 1)),
                    List.of(), null, "LASTSLOT"
            );
            checkoutService.createReceipt(request);
            return new Outcome(true, null);
        } catch (Exception e) {
            return new Outcome(false, e.getMessage());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private Customer newCustomer(String name, String phone) {
        Customer customer = new Customer();
        customer.setName(name);
        customer.setPhone(phone);
        customer.setCustomerType(Customer.CustomerType.MISC);
        customer.setIsActive(true);
        return customerRepository.save(customer);
    }

    private record Outcome(boolean succeeded, String failureMessage) {}
}
