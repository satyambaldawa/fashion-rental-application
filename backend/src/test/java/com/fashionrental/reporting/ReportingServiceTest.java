package com.fashionrental.reporting;

import com.fashionrental.common.util.DateTimeUtil;
import com.fashionrental.customer.Customer;
import com.fashionrental.invoice.Invoice;
import com.fashionrental.invoice.InvoiceRepository;
import com.fashionrental.receipt.Receipt;
import com.fashionrental.receipt.ReceiptRepository;
import com.fashionrental.reporting.model.response.DailyRevenueResponse;
import com.fashionrental.reporting.model.response.DailyRevenueSummary;
import com.fashionrental.reporting.model.response.DiscountsGivenResponse;
import com.fashionrental.reporting.model.response.MonthlyRevenueResponse;
import com.fashionrental.reporting.model.response.OutstandingDepositsResponse;
import com.fashionrental.reporting.model.response.OverdueRentalsResponse;
import com.fashionrental.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportingServiceTest {

    @Mock private ReceiptRepository receiptRepository;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private DateTimeUtil dateTimeUtil;
    @InjectMocks private ReportingService reportingService;

    private Receipt receiptA;   // rent 300, deposit 1000
    private Receipt receiptB;   // rent 200, deposit 500
    private Invoice refund;     // REFUND 800
    private Invoice collect;    // COLLECT 150

    @BeforeEach
    void setUp() {
        Customer customer = TestData.customer("Ravi", "9800000000");
        OffsetDateTime start = OffsetDateTime.parse("2026-04-10T10:00:00+05:30");
        OffsetDateTime end = OffsetDateTime.parse("2026-04-12T10:00:00+05:30");
        receiptA = TestData.receipt(customer, start, end, TestData.receiptLineItem(TestData.item("Sherwani", 300, 1000), 1));
        receiptB = TestData.receipt(customer, start, end, TestData.receiptLineItem(TestData.item("Turban", 200, 500), 1));
        refund = TestData.invoice(Invoice.TransactionType.REFUND, 800, 50, 0);
        collect = TestData.invoice(Invoice.TransactionType.COLLECT, 150, 100, 200);
    }

    @Test
    void getDailyRevenue_aggregates_receipts_and_invoices() {
        when(receiptRepository.findByCreatedAtBetweenOrderByCreatedAtAsc(any(), any()))
                .thenReturn(List.of(receiptA, receiptB));
        when(invoiceRepository.findByCreatedAtBetweenOrderByCreatedAtAsc(any(), any()))
                .thenReturn(List.of(refund, collect));

        DailyRevenueResponse report = reportingService.getDailyRevenue(LocalDate.of(2026, 4, 12));

        assertThat(report.rentCollected()).isEqualTo(500);
        assertThat(report.depositsCollected()).isEqualTo(1500);
        assertThat(report.depositsRefunded()).isEqualTo(800);
        assertThat(report.collectedFromCustomers()).isEqualTo(150);
        assertThat(report.lateFeeIncome()).isEqualTo(150);
        assertThat(report.damageIncome()).isEqualTo(200);
        assertThat(report.totalDiscountsGiven()).isZero();
        assertThat(report.netFlow()).isEqualTo(500 + 1500 + 150 - 800);
        assertThat(report.newReceiptsCount()).isEqualTo(2);
        assertThat(report.returnsProcessedCount()).isEqualTo(2);
    }

    @Test
    void getDailyRevenue_nets_discounts_given_out_of_the_flow_while_leaving_rent_gross() {
        receiptA.setCouponCode("SAVE20");
        receiptA.setDiscountAmount(60);
        when(receiptRepository.findByCreatedAtBetweenOrderByCreatedAtAsc(any(), any()))
                .thenReturn(List.of(receiptA, receiptB));
        when(invoiceRepository.findByCreatedAtBetweenOrderByCreatedAtAsc(any(), any()))
                .thenReturn(List.of());

        DailyRevenueResponse report = reportingService.getDailyRevenue(LocalDate.of(2026, 4, 12));

        assertThat(report.rentCollected()).isEqualTo(500); // stays gross
        assertThat(report.totalDiscountsGiven()).isEqualTo(60);
        assertThat(report.netFlow()).isEqualTo(500 - 60 + 1500);
    }

    @Test
    void getDailyRevenue_is_all_zeros_when_no_activity() {
        when(receiptRepository.findByCreatedAtBetweenOrderByCreatedAtAsc(any(), any())).thenReturn(List.of());
        when(invoiceRepository.findByCreatedAtBetweenOrderByCreatedAtAsc(any(), any())).thenReturn(List.of());

        DailyRevenueResponse report = reportingService.getDailyRevenue(LocalDate.of(2026, 4, 12));

        assertThat(report.rentCollected()).isZero();
        assertThat(report.netFlow()).isZero();
        assertThat(report.newReceiptsCount()).isZero();
    }

    @Test
    void getOutstandingDeposits_sums_active_receipt_deposits() {
        when(receiptRepository.findByStatusOrderByEndDatetimeAsc(Receipt.Status.GIVEN))
                .thenReturn(List.of(receiptA, receiptB));

        OutstandingDepositsResponse report = reportingService.getOutstandingDeposits();

        assertThat(report.totalOutstanding()).isEqualTo(1500);
        assertThat(report.items()).hasSize(2);
        assertThat(report.items().get(0).itemNames()).isNotEmpty();
    }

    @Test
    void getOverdueRentals_lists_overdue_receipts_with_hours() {
        when(receiptRepository.findByStatusAndEndDatetimeBeforeOrderByEndDatetimeAsc(eq(Receipt.Status.GIVEN), any()))
                .thenReturn(List.of(receiptA));
        when(dateTimeUtil.calculateOverdueHours(any(), any())).thenReturn(26.5);

        OverdueRentalsResponse report = reportingService.getOverdueRentals();

        assertThat(report.overdueCount()).isEqualTo(1);
        assertThat(report.items()).hasSize(1);
        assertThat(report.items().get(0).overdueHours()).isEqualTo(26.5);
    }

    @Test
    void getMonthlyRevenue_builds_one_entry_per_calendar_day_and_totals() {
        OffsetDateTime createdInApril = OffsetDateTime.parse("2026-04-10T10:00:00+05:30");
        TestData.withCreatedAt(receiptA, createdInApril);
        TestData.withCreatedAt(receiptB, createdInApril);
        TestData.withCreatedAt(refund, createdInApril);
        TestData.withCreatedAt(collect, createdInApril);
        when(receiptRepository.findByCreatedAtBetweenOrderByCreatedAtAsc(any(), any()))
                .thenReturn(List.of(receiptA, receiptB));
        when(invoiceRepository.findByCreatedAtBetweenOrderByCreatedAtAsc(any(), any()))
                .thenReturn(List.of(refund, collect));

        MonthlyRevenueResponse report = reportingService.getMonthlyRevenue(2026, 4);

        assertThat(report.dailyBreakdown()).hasSize(30); // April
        assertThat(report.totalRentCollected()).isEqualTo(500);
        assertThat(report.totalDepositsRefunded()).isEqualTo(800);
        assertThat(report.totalDiscountsGiven()).isZero();
        assertThat(report.totalNetFlow()).isEqualTo(500 + 1500 + 150 - 800);
    }

    @Test
    void getMonthlyRevenue_nets_discounts_given_out_of_the_total_flow() {
        OffsetDateTime createdInApril = OffsetDateTime.parse("2026-04-10T10:00:00+05:30");
        receiptA.setCouponCode("SAVE20");
        receiptA.setDiscountAmount(60);
        TestData.withCreatedAt(receiptA, createdInApril);
        TestData.withCreatedAt(receiptB, createdInApril);
        when(receiptRepository.findByCreatedAtBetweenOrderByCreatedAtAsc(any(), any()))
                .thenReturn(List.of(receiptA, receiptB));
        when(invoiceRepository.findByCreatedAtBetweenOrderByCreatedAtAsc(any(), any()))
                .thenReturn(List.of());

        MonthlyRevenueResponse report = reportingService.getMonthlyRevenue(2026, 4);

        assertThat(report.totalRentCollected()).isEqualTo(500); // stays gross
        assertThat(report.totalDiscountsGiven()).isEqualTo(60);
        assertThat(report.totalNetFlow()).isEqualTo(500 - 60 + 1500);

        DailyRevenueSummary aprilTenth = report.dailyBreakdown().stream()
                .filter(d -> d.date().equals(LocalDate.of(2026, 4, 10)))
                .findFirst().orElseThrow();
        assertThat(aprilTenth.totalDiscountsGiven()).isEqualTo(60);
    }

    // ─── getDiscountsGiven() ────────────────────────────────────────────────

    @Test
    void getDiscountsGiven_sums_and_groups_by_coupon_code() {
        receiptA.setCouponCode("SAVE20");
        receiptA.setDiscountAmount(60);
        receiptB.setCouponCode("SAVE20");
        receiptB.setDiscountAmount(40);
        Receipt receiptC = TestData.receipt(
                TestData.customer(), receiptA.getStartDatetime(), receiptA.getEndDatetime(),
                TestData.receiptLineItem(TestData.item("Blazer", 150, 400), 1));
        receiptC.setCouponCode("WELCOME10");
        receiptC.setDiscountAmount(15);

        when(receiptRepository.findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(any(), any()))
                .thenReturn(List.of(receiptA, receiptB, receiptC));

        DiscountsGivenResponse report = reportingService.getDiscountsGiven(
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30));

        assertThat(report.totalDiscountGiven()).isEqualTo(115);
        assertThat(report.receiptsWithCoupon()).isEqualTo(3);
        assertThat(report.byCoupon()).hasSize(2);
        assertThat(report.byCoupon().get(0).couponCode()).isEqualTo("SAVE20");
        assertThat(report.byCoupon().get(0).timesApplied()).isEqualTo(2);
        assertThat(report.byCoupon().get(0).totalDiscount()).isEqualTo(100);
        assertThat(report.byCoupon().get(1).couponCode()).isEqualTo("WELCOME10");
        assertThat(report.byCoupon().get(1).totalDiscount()).isEqualTo(15);
    }

    @Test
    void getDiscountsGiven_excludes_receipts_without_a_coupon() {
        when(receiptRepository.findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(any(), any()))
                .thenReturn(List.of(receiptA, receiptB));

        DiscountsGivenResponse report = reportingService.getDiscountsGiven(
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30));

        assertThat(report.totalDiscountGiven()).isZero();
        assertThat(report.receiptsWithCoupon()).isZero();
        assertThat(report.byCoupon()).isEmpty();
    }

    @Test
    void getDiscountsGiven_counts_a_coupon_that_floored_to_zero_discount() {
        // A valid coupon can compute to ₹0 (e.g. 1% of a small subtotal). It was still
        // applied and belongs in the counts, contributing 0 to the sums.
        receiptA.setCouponCode("TINY1");
        receiptA.setDiscountAmount(0);
        when(receiptRepository.findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(any(), any()))
                .thenReturn(List.of(receiptA));

        DiscountsGivenResponse report = reportingService.getDiscountsGiven(
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30));

        assertThat(report.receiptsWithCoupon()).isEqualTo(1);
        assertThat(report.byCoupon()).hasSize(1);
        assertThat(report.byCoupon().get(0).totalDiscount()).isZero();
        assertThat(report.totalDiscountGiven()).isZero();
    }

    @Test
    void getDiscountsGiven_rejects_a_reversed_range() {
        assertThatThrownBy(() -> reportingService.getDiscountsGiven(
                LocalDate.of(2026, 4, 30), LocalDate.of(2026, 4, 1)))
                .isInstanceOf(com.fashionrental.common.exception.ValidationException.class);
    }
}
