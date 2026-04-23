package com.fashionrental.reporting;

import com.fashionrental.common.util.DateTimeUtil;
import com.fashionrental.invoice.Invoice;
import com.fashionrental.invoice.InvoiceRepository;
import com.fashionrental.receipt.Receipt;
import com.fashionrental.receipt.ReceiptRepository;
import com.fashionrental.reporting.model.response.DailyRevenueResponse;
import com.fashionrental.reporting.model.response.DailyRevenueSummary;
import com.fashionrental.reporting.model.response.MonthlyRevenueResponse;
import com.fashionrental.reporting.model.response.OutstandingDepositItem;
import com.fashionrental.reporting.model.response.OutstandingDepositsResponse;
import com.fashionrental.reporting.model.response.OverdueRentalItem;
import com.fashionrental.reporting.model.response.OverdueRentalsResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ReportingService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final ReceiptRepository receiptRepository;
    private final InvoiceRepository invoiceRepository;
    private final DateTimeUtil dateTimeUtil;

    public ReportingService(ReceiptRepository receiptRepository,
                            InvoiceRepository invoiceRepository,
                            DateTimeUtil dateTimeUtil) {
        this.receiptRepository = receiptRepository;
        this.invoiceRepository = invoiceRepository;
        this.dateTimeUtil = dateTimeUtil;
    }

    public DailyRevenueResponse getDailyRevenue(LocalDate date) {
        OffsetDateTime dayStart = date.atStartOfDay(IST).toOffsetDateTime();
        OffsetDateTime dayEnd   = date.plusDays(1).atStartOfDay(IST).toOffsetDateTime();

        List<Receipt> receiptsCreated = receiptRepository.findByCreatedAtBetweenOrderByCreatedAtAsc(dayStart, dayEnd);
        int rentCollected     = receiptsCreated.stream().mapToInt(Receipt::getTotalRent).sum();
        int depositsCollected = receiptsCreated.stream().mapToInt(Receipt::getTotalDeposit).sum();

        List<Invoice> invoicesSettled = invoiceRepository.findByCreatedAtBetweenOrderByCreatedAtAsc(dayStart, dayEnd);
        int depositsRefunded = invoicesSettled.stream()
                .filter(i -> i.getTransactionType() == Invoice.TransactionType.REFUND)
                .mapToInt(Invoice::getFinalAmount).sum();
        int collectedFromCustomers = invoicesSettled.stream()
                .filter(i -> i.getTransactionType() == Invoice.TransactionType.COLLECT)
                .mapToInt(Invoice::getFinalAmount).sum();
        int lateFeeIncome  = invoicesSettled.stream().mapToInt(Invoice::getTotalLateFee).sum();
        int damageIncome   = invoicesSettled.stream().mapToInt(Invoice::getTotalDamageCost).sum();

        int netFlow = rentCollected + depositsCollected + collectedFromCustomers - depositsRefunded;

        return new DailyRevenueResponse(
                date,
                rentCollected, depositsCollected, depositsRefunded,
                collectedFromCustomers, lateFeeIncome, damageIncome,
                netFlow,
                receiptsCreated.size(), invoicesSettled.size()
        );
    }

    public OutstandingDepositsResponse getOutstandingDeposits() {
        List<Receipt> activeReceipts = receiptRepository.findByStatusOrderByEndDatetimeAsc(Receipt.Status.GIVEN);
        int totalOutstanding = activeReceipts.stream().mapToInt(Receipt::getTotalDeposit).sum();

        List<OutstandingDepositItem> items = activeReceipts.stream().map(r -> {
            int daysSinceRented = (int) ChronoUnit.DAYS.between(r.getStartDatetime(), OffsetDateTime.now());
            List<String> itemNames = r.getLineItems().stream()
                    .map(li -> li.getItem().getName() + " ×" + li.getQuantity())
                    .toList();
            return new OutstandingDepositItem(
                    r.getId(), r.getReceiptNumber(),
                    r.getCustomer().getName(), r.getCustomer().getPhone(),
                    itemNames, r.getTotalDeposit(), r.getEndDatetime(),
                    daysSinceRented
            );
        }).toList();

        return new OutstandingDepositsResponse(totalOutstanding, items);
    }

    public MonthlyRevenueResponse getMonthlyRevenue(int year, int month) {
        YearMonth yearMonth = YearMonth.of(year, month);
        OffsetDateTime monthStart = yearMonth.atDay(1).atStartOfDay(IST).toOffsetDateTime();
        OffsetDateTime monthEnd   = yearMonth.plusMonths(1).atDay(1).atStartOfDay(IST).toOffsetDateTime();

        List<Receipt> receipts = receiptRepository.findByCreatedAtBetweenOrderByCreatedAtAsc(monthStart, monthEnd);
        List<Invoice> invoices = invoiceRepository.findByCreatedAtBetweenOrderByCreatedAtAsc(monthStart, monthEnd);

        // Group by day (in IST)
        Map<LocalDate, List<Receipt>> receiptsByDay = receipts.stream()
                .collect(Collectors.groupingBy(r -> r.getCreatedAt().atZoneSameInstant(IST).toLocalDate()));
        Map<LocalDate, List<Invoice>> invoicesByDay = invoices.stream()
                .collect(Collectors.groupingBy(i -> i.getCreatedAt().atZoneSameInstant(IST).toLocalDate()));

        // Build one entry per calendar day in the month (zeros for days with no activity)
        List<DailyRevenueSummary> dailyBreakdown = new ArrayList<>();
        for (int day = 1; day <= yearMonth.lengthOfMonth(); day++) {
            LocalDate date = yearMonth.atDay(day);
            List<Receipt> dayReceipts = receiptsByDay.getOrDefault(date, List.of());
            List<Invoice> dayInvoices = invoicesByDay.getOrDefault(date, List.of());

            int rentCollected     = dayReceipts.stream().mapToInt(Receipt::getTotalRent).sum();
            int depositsCollected = dayReceipts.stream().mapToInt(Receipt::getTotalDeposit).sum();
            int depositsRefunded  = dayInvoices.stream()
                    .filter(i -> i.getTransactionType() == Invoice.TransactionType.REFUND)
                    .mapToInt(Invoice::getFinalAmount).sum();
            int collectedFromCustomers = dayInvoices.stream()
                    .filter(i -> i.getTransactionType() == Invoice.TransactionType.COLLECT)
                    .mapToInt(Invoice::getFinalAmount).sum();
            int lateFeeIncome = dayInvoices.stream().mapToInt(Invoice::getTotalLateFee).sum();
            int damageIncome  = dayInvoices.stream().mapToInt(Invoice::getTotalDamageCost).sum();
            int netFlow = rentCollected + depositsCollected + collectedFromCustomers - depositsRefunded;

            dailyBreakdown.add(new DailyRevenueSummary(
                    date, rentCollected, depositsCollected,
                    depositsRefunded, collectedFromCustomers,
                    lateFeeIncome, damageIncome, netFlow
            ));
        }

        // Monthly totals
        int totalRentCollected         = receipts.stream().mapToInt(Receipt::getTotalRent).sum();
        int totalDepositsCollected     = receipts.stream().mapToInt(Receipt::getTotalDeposit).sum();
        int totalDepositsRefunded      = invoices.stream()
                .filter(i -> i.getTransactionType() == Invoice.TransactionType.REFUND)
                .mapToInt(Invoice::getFinalAmount).sum();
        int totalCollectedFromCustomers = invoices.stream()
                .filter(i -> i.getTransactionType() == Invoice.TransactionType.COLLECT)
                .mapToInt(Invoice::getFinalAmount).sum();
        int totalLateFeeIncome = invoices.stream().mapToInt(Invoice::getTotalLateFee).sum();
        int totalDamageIncome  = invoices.stream().mapToInt(Invoice::getTotalDamageCost).sum();
        int totalNetFlow = totalRentCollected + totalDepositsCollected + totalCollectedFromCustomers - totalDepositsRefunded;

        return new MonthlyRevenueResponse(
                year, month,
                totalRentCollected, totalDepositsCollected, totalDepositsRefunded,
                totalCollectedFromCustomers, totalLateFeeIncome, totalDamageIncome,
                totalNetFlow, dailyBreakdown
        );
    }

    public OverdueRentalsResponse getOverdueRentals() {
        OffsetDateTime now = OffsetDateTime.now();
        List<Receipt> overdueReceipts = receiptRepository
                .findByStatusAndEndDatetimeBeforeOrderByEndDatetimeAsc(Receipt.Status.GIVEN, now);

        List<OverdueRentalItem> items = overdueReceipts.stream().map(r -> {
            double overdueHours = dateTimeUtil.calculateOverdueHours(r.getEndDatetime(), now);
            List<String> itemNames = r.getLineItems().stream()
                    .map(li -> li.getItem().getName() + " ×" + li.getQuantity())
                    .toList();
            return new OverdueRentalItem(
                    r.getId(), r.getReceiptNumber(),
                    r.getCustomer().getName(), r.getCustomer().getPhone(),
                    itemNames, r.getEndDatetime(), overdueHours
            );
        }).toList();

        return new OverdueRentalsResponse(items.size(), items);
    }
}
