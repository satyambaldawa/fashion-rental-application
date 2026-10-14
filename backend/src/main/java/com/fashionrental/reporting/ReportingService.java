package com.fashionrental.reporting;

import com.fashionrental.common.exception.ValidationException;
import com.fashionrental.common.util.DateTimeUtil;
import com.fashionrental.invoice.Invoice;
import com.fashionrental.invoice.InvoiceReportTotals;
import com.fashionrental.invoice.InvoiceRepository;
import com.fashionrental.invoice.ReceiptItemCount;
import com.fashionrental.receipt.Receipt;
import com.fashionrental.receipt.ReceiptRepository;
import com.fashionrental.reporting.model.response.CouponDiscountSummary;
import com.fashionrental.reporting.model.response.DailyRevenueResponse;
import com.fashionrental.reporting.model.response.DailyRevenueSummary;
import com.fashionrental.reporting.model.response.DiscountsGivenResponse;
import com.fashionrental.reporting.model.response.InvoiceListItem;
import com.fashionrental.reporting.model.response.InvoicesReportResponse;
import com.fashionrental.reporting.model.response.MonthlyRevenueResponse;
import com.fashionrental.reporting.model.response.OutstandingDepositItem;
import com.fashionrental.reporting.model.response.OutstandingDepositsResponse;
import com.fashionrental.reporting.model.response.OverdueRentalItem;
import com.fashionrental.reporting.model.response.OverdueRentalsResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ReportingService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    // Caps the unpaged invoices list at a year so the response stays small.
    static final int MAX_INVOICE_REPORT_DAYS = 366;
    static final int MAX_INVOICE_REPORT_PAGE_SIZE = 100;
    // id breaks ties so invoices created in the same instant never repeat or vanish across pages.
    private static final Sort NEWEST_INVOICE_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

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
        int discountsGiven    = receiptsCreated.stream().mapToInt(Receipt::getDiscountAmount).sum();

        List<Invoice> invoicesSettled = invoiceRepository.findByCreatedAtBetweenOrderByCreatedAtAsc(dayStart, dayEnd);
        int depositsRefunded = invoicesSettled.stream()
                .filter(i -> i.getTransactionType() == Invoice.TransactionType.REFUND)
                .mapToInt(Invoice::getFinalAmount).sum();
        int collectedFromCustomers = invoicesSettled.stream()
                .filter(i -> i.getTransactionType() == Invoice.TransactionType.COLLECT)
                .mapToInt(Invoice::getFinalAmount).sum();
        int lateFeeIncome  = invoicesSettled.stream().mapToInt(Invoice::getTotalLateFee).sum();
        int damageIncome   = invoicesSettled.stream().mapToInt(Invoice::getTotalDamageCost).sum();

        List<Receipt> receiptsCancelled = receiptRepository.findByCancelledAtBetweenOrderByCancelledAtAsc(dayStart, dayEnd);
        int cancellationRefunds = sumCancellationRefunds(receiptsCancelled);

        // rentCollected stays gross so the label stays truthful; discountsGiven is netted
        // out separately so netFlow reflects cash actually received.
        int netFlow = rentCollected - discountsGiven + depositsCollected + collectedFromCustomers
                - depositsRefunded - cancellationRefunds;

        return new DailyRevenueResponse(
                date,
                rentCollected, depositsCollected, depositsRefunded,
                collectedFromCustomers, lateFeeIncome, damageIncome,
                discountsGiven, netFlow,
                receiptsCreated.size(), invoicesSettled.size(),
                cancellationRefunds, receiptsCancelled.size()
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
        List<Receipt> cancellations = receiptRepository.findByCancelledAtBetweenOrderByCancelledAtAsc(monthStart, monthEnd);

        // Group by day (in IST)
        Map<LocalDate, List<Receipt>> receiptsByDay = receipts.stream()
                .collect(Collectors.groupingBy(r -> r.getCreatedAt().atZoneSameInstant(IST).toLocalDate()));
        Map<LocalDate, List<Invoice>> invoicesByDay = invoices.stream()
                .collect(Collectors.groupingBy(i -> i.getCreatedAt().atZoneSameInstant(IST).toLocalDate()));
        Map<LocalDate, List<Receipt>> cancellationsByDay = cancellations.stream()
                .collect(Collectors.groupingBy(r -> r.getCancelledAt().atZoneSameInstant(IST).toLocalDate()));

        // Build one entry per calendar day in the month (zeros for days with no activity)
        List<DailyRevenueSummary> dailyBreakdown = new ArrayList<>();
        for (int day = 1; day <= yearMonth.lengthOfMonth(); day++) {
            LocalDate date = yearMonth.atDay(day);
            List<Receipt> dayReceipts = receiptsByDay.getOrDefault(date, List.of());
            List<Invoice> dayInvoices = invoicesByDay.getOrDefault(date, List.of());
            List<Receipt> dayCancellations = cancellationsByDay.getOrDefault(date, List.of());

            int rentCollected     = dayReceipts.stream().mapToInt(Receipt::getTotalRent).sum();
            int depositsCollected = dayReceipts.stream().mapToInt(Receipt::getTotalDeposit).sum();
            int discountsGiven    = dayReceipts.stream().mapToInt(Receipt::getDiscountAmount).sum();
            int depositsRefunded  = dayInvoices.stream()
                    .filter(i -> i.getTransactionType() == Invoice.TransactionType.REFUND)
                    .mapToInt(Invoice::getFinalAmount).sum();
            int collectedFromCustomers = dayInvoices.stream()
                    .filter(i -> i.getTransactionType() == Invoice.TransactionType.COLLECT)
                    .mapToInt(Invoice::getFinalAmount).sum();
            int lateFeeIncome = dayInvoices.stream().mapToInt(Invoice::getTotalLateFee).sum();
            int damageIncome  = dayInvoices.stream().mapToInt(Invoice::getTotalDamageCost).sum();
            int cancellationRefunds = sumCancellationRefunds(dayCancellations);
            int netFlow = rentCollected - discountsGiven + depositsCollected + collectedFromCustomers
                    - depositsRefunded - cancellationRefunds;

            dailyBreakdown.add(new DailyRevenueSummary(
                    date, rentCollected, depositsCollected,
                    depositsRefunded, collectedFromCustomers,
                    lateFeeIncome, damageIncome, discountsGiven, netFlow,
                    cancellationRefunds
            ));
        }

        // Monthly totals
        int totalRentCollected         = receipts.stream().mapToInt(Receipt::getTotalRent).sum();
        int totalDepositsCollected     = receipts.stream().mapToInt(Receipt::getTotalDeposit).sum();
        int totalDiscountsGiven        = receipts.stream().mapToInt(Receipt::getDiscountAmount).sum();
        int totalDepositsRefunded      = invoices.stream()
                .filter(i -> i.getTransactionType() == Invoice.TransactionType.REFUND)
                .mapToInt(Invoice::getFinalAmount).sum();
        int totalCollectedFromCustomers = invoices.stream()
                .filter(i -> i.getTransactionType() == Invoice.TransactionType.COLLECT)
                .mapToInt(Invoice::getFinalAmount).sum();
        int totalLateFeeIncome = invoices.stream().mapToInt(Invoice::getTotalLateFee).sum();
        int totalDamageIncome  = invoices.stream().mapToInt(Invoice::getTotalDamageCost).sum();
        int totalCancellationRefunds = sumCancellationRefunds(cancellations);
        int totalNetFlow = totalRentCollected - totalDiscountsGiven + totalDepositsCollected
                + totalCollectedFromCustomers - totalDepositsRefunded - totalCancellationRefunds;

        return new MonthlyRevenueResponse(
                year, month,
                totalRentCollected, totalDepositsCollected, totalDepositsRefunded,
                totalCollectedFromCustomers, totalLateFeeIncome, totalDamageIncome,
                totalDiscountsGiven, totalNetFlow, dailyBreakdown,
                totalCancellationRefunds
        );
    }

    public DiscountsGivenResponse getDiscountsGiven(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new ValidationException("'to' must not be before 'from'.");
        }

        OffsetDateTime rangeStart = from.atStartOfDay(IST).toOffsetDateTime();
        OffsetDateTime rangeEnd   = to.plusDays(1).atStartOfDay(IST).toOffsetDateTime();

        // Unlike the fixed daily/monthly reports (where the inclusive BETWEEN's double-count
        // at midnight is accepted — see plan §10), this endpoint takes a user-chosen range:
        // querying 1-30 Jun and then 1-31 Jul with an inclusive upper bound would double-count
        // a receipt created at exactly 2026-07-01T00:00:00+05:30 in both totals. The exclusive
        // variant closes that.
        List<Receipt> receipts = receiptRepository
                .findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(rangeStart, rangeEnd);

        // couponCode != null, not discountAmount > 0: a coupon that floored to a ₹0
        // discount was still applied and belongs in the counts, contributing 0 to the sums.
        List<Receipt> withCoupon = receipts.stream()
                .filter(r -> r.getCouponCode() != null)
                .toList();

        int totalDiscountGiven = withCoupon.stream().mapToInt(Receipt::getDiscountAmount).sum();

        Map<String, List<Receipt>> byCode = withCoupon.stream()
                .collect(Collectors.groupingBy(Receipt::getCouponCode, LinkedHashMap::new, Collectors.toList()));

        List<CouponDiscountSummary> byCoupon = byCode.entrySet().stream()
                .map(e -> new CouponDiscountSummary(
                        e.getKey(),
                        e.getValue().size(),
                        e.getValue().stream().mapToInt(Receipt::getDiscountAmount).sum()))
                .sorted(Comparator.comparingInt(CouponDiscountSummary::totalDiscount).reversed())
                .toList();

        return new DiscountsGivenResponse(from, to, totalDiscountGiven, withCoupon.size(), byCoupon);
    }

    public InvoicesReportResponse getInvoicesReport(LocalDate from, LocalDate to, Integer minRevenue,
                                                    int page, int pageSize) {
        if (to.isBefore(from)) {
            throw new ValidationException("'to' must not be before 'from'.");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_INVOICE_REPORT_DAYS) {
            throw new ValidationException("Date range must not exceed " + MAX_INVOICE_REPORT_DAYS + " days.");
        }
        if (minRevenue != null && minRevenue < 0) {
            throw new ValidationException("'minRevenue' must not be negative.");
        }
        if (page < 0) {
            throw new ValidationException("'page' must not be negative.");
        }
        if (pageSize < 1 || pageSize > MAX_INVOICE_REPORT_PAGE_SIZE) {
            throw new ValidationException("'size' must be between 1 and " + MAX_INVOICE_REPORT_PAGE_SIZE + ".");
        }

        OffsetDateTime rangeStart = from.atStartOfDay(IST).toOffsetDateTime();
        OffsetDateTime rangeEnd   = to.plusDays(1).atStartOfDay(IST).toOffsetDateTime();
        // Revenue is never negative (discount ≤ rent, fees ≥ 0), so "no minimum" is a minimum of 0.
        int effectiveMinRevenue = minRevenue != null ? minRevenue : 0;

        Page<Invoice> invoicePage = invoiceRepository.findReportPage(
                rangeStart, rangeEnd, effectiveMinRevenue, PageRequest.of(page, pageSize, NEWEST_INVOICE_FIRST));
        Map<UUID, Long> itemsByReceipt = itemsRentedByReceipt(invoicePage.getContent());
        List<InvoiceListItem> rows = invoicePage.getContent().stream()
                .map(invoice -> toInvoiceListItem(invoice,
                        itemsByReceipt.getOrDefault(invoice.getReceipt().getId(), 0L)))
                .toList();

        InvoiceReportTotals totals = invoiceRepository.sumReportTotals(rangeStart, rangeEnd, effectiveMinRevenue);
        long totalItems = invoiceRepository.sumReportItemsRented(rangeStart, rangeEnd, effectiveMinRevenue);
        long totalRevenue = totals.totalRent() - totals.totalDiscount() + totals.totalLateFee() + totals.totalDamage();

        return new InvoicesReportResponse(
                from, to, minRevenue,
                page, pageSize, invoicePage.getTotalPages(),
                Math.toIntExact(totals.invoiceCount()),
                Math.toIntExact(totalItems),
                Math.toIntExact(totals.totalRent()),
                Math.toIntExact(totals.totalDiscount()),
                Math.toIntExact(totals.totalLateFee()),
                Math.toIntExact(totals.totalDamage()),
                Math.toIntExact(totalRevenue),
                rows
        );
    }

    private Map<UUID, Long> itemsRentedByReceipt(List<Invoice> invoices) {
        if (invoices.isEmpty()) {
            return Map.of();
        }
        List<UUID> receiptIds = invoices.stream().map(i -> i.getReceipt().getId()).toList();
        return invoiceRepository.countItemsRentedByReceipt(receiptIds).stream()
                .collect(Collectors.toMap(ReceiptItemCount::receiptId, ReceiptItemCount::itemCount));
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

    // Rent is read gross from the receipt because invoices.total_rent is already net of the
    // coupon (ReturnService) — subtracting the discount from it would count the coupon twice.
    // Deposits are held and refunded, never revenue, so neither total_deposit_collected nor
    // the settlement final_amount belongs in this figure.
    private static InvoiceListItem toInvoiceListItem(Invoice invoice, long itemsRented) {
        Receipt receipt = invoice.getReceipt();
        int grossRent = receipt.getTotalRent();
        int discount = receipt.getDiscountAmount();
        int revenue = grossRent - discount + invoice.getTotalLateFee() + invoice.getTotalDamageCost();
        return new InvoiceListItem(
                invoice.getId(), invoice.getInvoiceNumber(), invoice.getCreatedAt(),
                invoice.getCustomer().getName(), receipt.getReceiptNumber(),
                Math.toIntExact(itemsRented), grossRent, discount, invoice.getTotalLateFee(), invoice.getTotalDamageCost(),
                revenue
        );
    }

    // grand_total is rent net of discount plus deposit (receipts_grand_total_check), so it is
    // exactly the full amount collected at checkout — which is what a cancellation refunds.
    private static int sumCancellationRefunds(List<Receipt> cancelledReceipts) {
        return cancelledReceipts.stream().mapToInt(Receipt::getGrandTotal).sum();
    }
}
