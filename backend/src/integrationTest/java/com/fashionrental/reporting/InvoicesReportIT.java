package com.fashionrental.reporting;

import com.fashionrental.AbstractIntegrationTest;
import com.fashionrental.configuration.Coupon;
import com.fashionrental.configuration.CouponRepository;
import com.fashionrental.customer.Customer;
import com.fashionrental.customer.CustomerRepository;
import com.fashionrental.inventory.Item;
import com.fashionrental.inventory.ItemRepository;
import com.fashionrental.inventory.PackageComponent;
import com.fashionrental.inventory.PackageComponentRepository;
import com.fashionrental.invoice.ReturnService;
import com.fashionrental.invoice.model.request.ProcessReturnRequest;
import com.fashionrental.invoice.model.request.ReturnLineItem;
import com.fashionrental.invoice.model.response.InvoiceResponse;
import com.fashionrental.receipt.CheckoutService;
import com.fashionrental.receipt.ReceiptGivenService;
import com.fashionrental.receipt.model.request.CheckoutLineItem;
import com.fashionrental.receipt.model.request.CheckoutRequest;
import com.fashionrental.receipt.model.response.ReceiptResponse;
import com.fashionrental.reporting.model.response.InvoiceListItem;
import com.fashionrental.reporting.model.response.InvoicesReportResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs real checkouts and returns against PostgreSQL, backdates the invoices' created_at, and
 * verifies the SQL behind ReportingService#getInvoicesReport: IST day boundaries with an
 * exclusive upper bound, the revenue filter (coupon counted once, minimum inclusive), paging
 * with range-wide totals, and item counts that treat a package as one item.
 */
@Transactional
class InvoicesReportIT extends AbstractIntegrationTest {

    private static final LocalDate JUNE_1 = LocalDate.of(2026, 6, 1);
    private static final LocalDate JUNE_7 = LocalDate.of(2026, 6, 7);
    private static final int PAGE_SIZE = 20;

    @Autowired private ReportingService reportingService;
    @Autowired private CheckoutService checkoutService;
    @Autowired private ReturnService returnService;
    @Autowired private ReceiptGivenService receiptGivenService;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private ItemRepository itemRepository;
    @Autowired private CouponRepository couponRepository;
    @Autowired private PackageComponentRepository packageComponentRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private Customer customer;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "owner", null, List.of(new SimpleGrantedAuthority("ROLE_OWNER"))));
        customer = newCustomer();
    }

    @Test
    void selects_invoices_by_ist_day_and_never_counts_a_midnight_invoice_in_two_ranges() {
        Item item = newItem("Anarkali", 600);

        InvoiceResponse inRange = rentAndReturn(List.of(line(item, 1)), null, "2026-06-03T15:00:00+05:30");
        InvoiceResponse atMidnight = rentAndReturn(List.of(line(item, 1)), null, "2026-06-08T00:00:00+05:30");
        rentAndReturn(List.of(line(item, 1)), null, "2026-05-31T23:59:59+05:30");
        flushAndClear();

        InvoicesReportResponse firstWeek = reportingService.getInvoicesReport(JUNE_1, JUNE_7, null, 0, PAGE_SIZE);
        InvoicesReportResponse nextDay = reportingService.getInvoicesReport(
                LocalDate.of(2026, 6, 8), LocalDate.of(2026, 6, 8), null, 0, PAGE_SIZE);

        assertThat(firstWeek.invoices()).extracting("invoiceId").containsExactly(inRange.id());
        assertThat(nextDay.invoices()).extracting("invoiceId").containsExactly(atMidnight.id());
        assertThat(firstWeek.invoices().get(0).customerName()).isEqualTo("Meera");
        assertThat(firstWeek.invoices().get(0).receiptNumber()).isEqualTo(inRange.receiptNumber());
    }

    @Test
    void counts_a_coupon_discount_exactly_once_in_row_and_total_revenue() {
        saveCoupon("INV20");
        Item item = newItem("Anarkali", 600);
        ReceiptResponse receipt = checkout(List.of(line(item, 1)), "INV20");
        backdateInvoice(processReturn(receipt).id(), "2026-06-03T15:00:00+05:30");
        flushAndClear();

        InvoicesReportResponse report = reportingService.getInvoicesReport(JUNE_1, JUNE_7, null, 0, PAGE_SIZE);
        InvoiceListItem row = report.invoices().get(0);

        int expectedRevenue = receipt.totalRent() - receipt.discountAmount();
        assertThat(receipt.discountAmount()).isPositive();
        assertThat(row.rent()).isEqualTo(receipt.totalRent());
        assertThat(row.discount()).isEqualTo(receipt.discountAmount());
        assertThat(row.revenue()).isEqualTo(expectedRevenue);
        assertThat(report.totalDiscount()).isEqualTo(receipt.discountAmount());
        assertThat(report.totalRevenue()).isEqualTo(expectedRevenue);
    }

    @Test
    void counts_a_package_as_one_item_and_sums_quantities_of_billed_lines() {
        Item turban = newItem("Turban", 200);
        Item weddingPackage = newItem("Wedding Package", 1000);
        weddingPackage.setItemType(Item.ItemType.PACKAGE);
        PackageComponent component = new PackageComponent();
        component.setPackageItem(weddingPackage);
        component.setComponentItem(turban);
        component.setQuantity(2);
        packageComponentRepository.save(component);
        Item sherwani = newItem("Sherwani", 300);

        rentAndReturn(List.of(line(weddingPackage, 1), line(sherwani, 2)), null, "2026-06-03T15:00:00+05:30");
        flushAndClear();

        InvoicesReportResponse report = reportingService.getInvoicesReport(JUNE_1, JUNE_7, null, 0, PAGE_SIZE);

        assertThat(report.invoices().get(0).items()).isEqualTo(3);
        assertThat(report.totalItems()).isEqualTo(3);
    }

    @Test
    void pages_newest_first_with_an_inclusive_minimum_and_totals_covering_every_page() {
        ReceiptResponse small = checkout(List.of(line(newItem("Dupatta", 300), 1)), null);
        ReceiptResponse medium = checkout(List.of(line(newItem("Lehenga", 500), 1)), null);
        ReceiptResponse large = checkout(List.of(line(newItem("Sherwani", 800), 1)), null);
        backdateInvoice(processReturn(small).id(), "2026-06-02T12:00:00+05:30");
        InvoiceResponse mediumInvoice = processReturn(medium);
        backdateInvoice(mediumInvoice.id(), "2026-06-03T12:00:00+05:30");
        InvoiceResponse largeInvoice = processReturn(large);
        backdateInvoice(largeInvoice.id(), "2026-06-04T12:00:00+05:30");
        flushAndClear();

        int minRevenue = medium.totalRent();
        InvoicesReportResponse firstPage = reportingService.getInvoicesReport(JUNE_1, JUNE_7, minRevenue, 0, 1);
        InvoicesReportResponse secondPage = reportingService.getInvoicesReport(JUNE_1, JUNE_7, minRevenue, 1, 1);

        assertThat(firstPage.invoices()).extracting("invoiceId").containsExactly(largeInvoice.id());
        assertThat(secondPage.invoices()).extracting("invoiceId").containsExactly(mediumInvoice.id());
        assertThat(firstPage.totalPages()).isEqualTo(2);
        assertThat(firstPage.invoiceCount()).isEqualTo(2);
        assertThat(firstPage.totalRevenue()).isEqualTo(medium.totalRent() + large.totalRent());
        assertThat(secondPage.totalRevenue()).isEqualTo(firstPage.totalRevenue());
    }

    @Test
    void reports_a_waived_late_fee_as_zero_late_fee_and_revenue() {
        Item item = newItem("Anarkali", 600);
        ReceiptResponse receipt = checkout(List.of(line(item, 1)), null);

        InvoiceResponse invoice = processReturn(receipt, receipt.endDatetime().plusHours(7), Boolean.FALSE);
        backdateInvoice(invoice.id(), "2026-06-03T15:00:00+05:30");
        flushAndClear();

        InvoicesReportResponse report = reportingService.getInvoicesReport(JUNE_1, JUNE_7, null, 0, PAGE_SIZE);
        InvoiceListItem row = report.invoices().get(0);

        assertThat(row.lateFee()).isZero();
        assertThat(report.totalLateFee()).isZero();
        assertThat(row.revenue()).isEqualTo(receipt.totalRent());

        InvoicesReportResponse excludingWaived = reportingService.getInvoicesReport(
                JUNE_1, JUNE_7, receipt.totalRent() + 1, 0, PAGE_SIZE);
        assertThat(excludingWaived.invoices()).isEmpty();
    }

    private InvoiceResponse rentAndReturn(List<CheckoutLineItem> lines, String couponCode, String invoicedAt) {
        InvoiceResponse invoice = processReturn(checkout(lines, couponCode));
        backdateInvoice(invoice.id(), invoicedAt);
        return invoice;
    }

    private ReceiptResponse checkout(List<CheckoutLineItem> lines, String couponCode) {
        OffsetDateTime start = OffsetDateTime.parse("2026-05-01T10:00:00+05:30");
        OffsetDateTime end = OffsetDateTime.parse("2026-05-02T10:00:00+05:30");
        return checkoutService.createReceipt(new CheckoutRequest(
                customer.getId(), start, end, lines, List.of(), null, couponCode, null, null, null));
    }

    private InvoiceResponse processReturn(ReceiptResponse receipt) {
        return processReturn(receipt, receipt.endDatetime(), null);
    }

    private InvoiceResponse processReturn(ReceiptResponse receipt, OffsetDateTime returnAt, Boolean applyLateFee) {
        receiptGivenService.markGiven(receipt.id());
        List<ReturnLineItem> returnLines = receipt.lineItems().stream()
                .map(li -> new ReturnLineItem(li.id(), false, null, null))
                .toList();
        return returnService.processReturn(receipt.id(), new ProcessReturnRequest(
                returnAt, returnLines, "CASH", null, null, applyLateFee));
    }

    private static CheckoutLineItem line(Item item, int quantity) {
        return new CheckoutLineItem(item.getId(), quantity);
    }

    private void backdateInvoice(UUID invoiceId, String createdAt) {
        entityManager.createNativeQuery("UPDATE invoices SET created_at = :createdAt WHERE id = :id")
                .setParameter("createdAt", OffsetDateTime.parse(createdAt))
                .setParameter("id", invoiceId)
                .executeUpdate();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private void saveCoupon(String code) {
        Coupon coupon = new Coupon();
        coupon.setCode(code);
        coupon.setDiscountType(Coupon.DiscountType.PERCENT);
        coupon.setValue(20);
        coupon.setValidFrom(OffsetDateTime.now().minusYears(1));
        coupon.setValidTo(OffsetDateTime.now().plusYears(1));
        coupon.setIsActive(true);
        couponRepository.save(coupon);
    }

    private Customer newCustomer() {
        Customer newCustomer = new Customer();
        newCustomer.setName("Meera");
        newCustomer.setPhone("9822200111");
        newCustomer.setCustomerType(Customer.CustomerType.MISC);
        newCustomer.setIsActive(true);
        return customerRepository.save(newCustomer);
    }

    private Item newItem(String name, int rate) {
        Item item = new Item();
        item.setName(name);
        item.setCategory(Item.Category.COSTUME);
        item.setItemType(Item.ItemType.INDIVIDUAL);
        item.setRate(rate);
        item.setDeposit(1500);
        item.setQuantity(5);
        item.setIsActive(true);
        return itemRepository.save(item);
    }
}
