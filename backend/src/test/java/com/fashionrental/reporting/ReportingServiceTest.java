package com.fashionrental.reporting;

import com.fashionrental.common.util.DateTimeUtil;
import com.fashionrental.customer.Customer;
import com.fashionrental.invoice.Invoice;
import com.fashionrental.invoice.InvoiceRepository;
import com.fashionrental.receipt.Receipt;
import com.fashionrental.receipt.ReceiptRepository;
import com.fashionrental.reporting.model.response.DailyRevenueResponse;
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
        assertThat(report.netFlow()).isEqualTo(500 + 1500 + 150 - 800);
        assertThat(report.newReceiptsCount()).isEqualTo(2);
        assertThat(report.returnsProcessedCount()).isEqualTo(2);
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
        assertThat(report.totalNetFlow()).isEqualTo(500 + 1500 + 150 - 800);
    }
}
