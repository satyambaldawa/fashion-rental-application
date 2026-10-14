package com.fashionrental.reporting.model.response;

import java.time.LocalDate;
import java.util.List;

public record InvoicesReportResponse(
        LocalDate from,
        LocalDate to,
        Integer minRevenue,
        int page,
        int pageSize,
        int totalPages,
        int invoiceCount,
        int totalItems,
        int totalRent,
        int totalDiscount,
        int totalLateFee,
        int totalDamage,
        int totalRevenue,
        List<InvoiceListItem> invoices
) {}
