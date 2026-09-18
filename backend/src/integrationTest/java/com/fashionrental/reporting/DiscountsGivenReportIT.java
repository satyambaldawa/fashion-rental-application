package com.fashionrental.reporting;

import com.fashionrental.AbstractIntegrationTest;
import com.fashionrental.configuration.Coupon;
import com.fashionrental.configuration.CouponRepository;
import com.fashionrental.customer.Customer;
import com.fashionrental.customer.CustomerRepository;
import com.fashionrental.inventory.Item;
import com.fashionrental.inventory.ItemRepository;
import com.fashionrental.receipt.CheckoutService;
import com.fashionrental.receipt.model.request.CheckoutLineItem;
import com.fashionrental.receipt.model.request.CheckoutRequest;
import com.fashionrental.reporting.model.response.DiscountsGivenResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks out real receipts against real PostgreSQL, backdates their created_at via native
 * SQL (createdAt has no setter and is Hibernate-managed), and verifies
 * ReportingService#getDiscountsGiven sums correctly over an IST range: it excludes a receipt
 * exactly at the exclusive upper boundary and one just before the lower boundary, includes
 * one squarely inside the range, and — the case a UTC-computed range would get wrong — also
 * includes one created at an instant that falls in a different UTC calendar day than its IST
 * one. None of this is reachable from a mocked repository test, since it never runs the real
 * query.
 */
@Transactional
class DiscountsGivenReportIT extends AbstractIntegrationTest {

    @Autowired private ReportingService reportingService;
    @Autowired private CheckoutService checkoutService;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private ItemRepository itemRepository;
    @Autowired private CouponRepository couponRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void sums_within_an_ist_range_honouring_the_exclusive_upper_boundary() {
        var auth = new UsernamePasswordAuthenticationToken(
                "owner", null, List.of(new SimpleGrantedAuthority("ROLE_OWNER")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        Customer customer = newCustomer("Priya", "9822200099");
        Item item = newItem("Lehenga", 500, 1500);

        Coupon coupon = new Coupon();
        coupon.setCode("RANGE20");
        coupon.setDiscountType(Coupon.DiscountType.PERCENT);
        coupon.setValue(20);
        coupon.setValidFrom(OffsetDateTime.now().minusYears(1));
        coupon.setValidTo(OffsetDateTime.now().plusYears(1));
        coupon.setIsActive(true);
        couponRepository.save(coupon);

        OffsetDateTime rentalStart = OffsetDateTime.parse("2026-05-01T10:00:00+05:30");
        OffsetDateTime rentalEnd = OffsetDateTime.parse("2026-05-02T10:00:00+05:30");

        var inRangeReceipt = checkout(customer, item, rentalStart, rentalEnd, "RANGE20");
        backdateCreatedAt(inRangeReceipt.id(), OffsetDateTime.parse("2026-06-15T12:00:00+05:30"));

        var justBeforeRangeReceipt = checkout(customer, item, rentalStart, rentalEnd, "RANGE20");
        // 2026-06-01T00:00:00+05:30 is the range's inclusive lower boundary; one second
        // before it must not be counted.
        backdateCreatedAt(justBeforeRangeReceipt.id(), OffsetDateTime.parse("2026-05-31T23:59:59+05:30"));

        var atUpperBoundaryReceipt = checkout(customer, item, rentalStart, rentalEnd, "RANGE20");
        // The repository method backing this endpoint is deliberately GreaterThanEqual /
        // LessThan (exclusive upper), not the inclusive BETWEEN the fixed-period reports use
        // — see ReportingService#getDiscountsGiven and ReceiptRepository. Placed at exactly
        // the boundary instant, not a second past it, so this pins the exclusivity itself
        // rather than merely a nearby instant.
        backdateCreatedAt(atUpperBoundaryReceipt.id(), OffsetDateTime.parse("2026-07-01T00:00:00+05:30"));

        var istOnlyReceipt = checkout(customer, item, rentalStart, rentalEnd, "RANGE20");
        // 2026-06-01T02:00 IST is 2026-05-31T20:30Z — inside the IST range, outside a range
        // computed against the JVM's default (UTC) zone. Proves the boundaries are IST, not
        // merely "some timezone that happens to agree most of the time."
        backdateCreatedAt(istOnlyReceipt.id(), OffsetDateTime.parse("2026-06-01T02:00:00+05:30"));

        entityManager.flush();
        entityManager.clear();

        DiscountsGivenResponse report = reportingService.getDiscountsGiven(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));

        assertThat(report.receiptsWithCoupon()).isEqualTo(2);
        assertThat(report.totalDiscountGiven())
                .isEqualTo(inRangeReceipt.discountAmount() + istOnlyReceipt.discountAmount());
        assertThat(report.byCoupon()).hasSize(1);
        assertThat(report.byCoupon().get(0).couponCode()).isEqualTo("RANGE20");
        assertThat(report.byCoupon().get(0).timesApplied()).isEqualTo(2);
    }

    private com.fashionrental.receipt.model.response.ReceiptResponse checkout(
            Customer customer, Item item, OffsetDateTime start, OffsetDateTime end, String couponCode) {
        return checkoutService.createReceipt(new CheckoutRequest(
                customer.getId(), start, end,
                List.of(new CheckoutLineItem(item.getId(), 1)),
                List.of(), null, couponCode
        ));
    }

    private void backdateCreatedAt(java.util.UUID receiptId, OffsetDateTime createdAt) {
        entityManager.createNativeQuery("UPDATE receipts SET created_at = :createdAt WHERE id = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", receiptId)
                .executeUpdate();
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
        item.setQuantity(4); // four checkouts share this item in the boundary test below
        item.setIsActive(true);
        return itemRepository.save(item);
    }
}
