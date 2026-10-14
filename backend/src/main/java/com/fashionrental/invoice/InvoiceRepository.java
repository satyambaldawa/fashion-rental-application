package com.fashionrental.invoice;

import com.fashionrental.receipt.ReceiptRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    long countByInvoiceNumberStartingWith(String prefix);

    List<Invoice> findByCreatedAtBetweenOrderByCreatedAtAsc(OffsetDateTime from, OffsetDateTime to);

    List<Invoice> findByReceipt_IdIn(List<UUID> receiptIds);

    // The invoices report's queries share one filter. The range's upper bound is exclusive so
    // adjacent user-chosen ranges never count the same invoice twice. The revenue expression must
    // stay in step with ReportingService#revenueOf; it uses the receipt's gross rent because
    // invoices.total_rent is already net of the coupon.
    String REPORT_FILTER = """
             where i.createdAt >= :from and i.createdAt < :to
             and (r.totalRent - r.discountAmount + i.totalLateFee + i.totalDamageCost) >= :minRevenue
            """;

    @Query(value = "select i from Invoice i join fetch i.receipt r join fetch i.customer" + REPORT_FILTER,
           countQuery = "select count(i) from Invoice i join i.receipt r" + REPORT_FILTER)
    Page<Invoice> findReportPage(@Param("from") OffsetDateTime from,
                                 @Param("to") OffsetDateTime to,
                                 @Param("minRevenue") int minRevenue,
                                 Pageable pageable);

    @Query("""
            select new com.fashionrental.invoice.InvoiceReportTotals(
                count(i),
                coalesce(sum(r.totalRent), 0L),
                coalesce(sum(r.discountAmount), 0L),
                coalesce(sum(i.totalLateFee), 0L),
                coalesce(sum(i.totalDamageCost), 0L))
            from Invoice i join i.receipt r
            """ + REPORT_FILTER)
    InvoiceReportTotals sumReportTotals(@Param("from") OffsetDateTime from,
                                        @Param("to") OffsetDateTime to,
                                        @Param("minRevenue") int minRevenue);

    @Query("select coalesce(sum(li.quantity), 0L) from Invoice i join i.receipt r join r.lineItems li"
            + REPORT_FILTER + " and" + ReceiptRepository.BILLED_LINE_ITEM)
    long sumReportItemsRented(@Param("from") OffsetDateTime from,
                              @Param("to") OffsetDateTime to,
                              @Param("minRevenue") int minRevenue);

    java.util.Optional<Invoice> findByShareToken(String shareToken);
}
