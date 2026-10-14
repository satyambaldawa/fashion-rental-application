package com.fashionrental.invoice;

public record InvoiceReportTotals(
        long invoiceCount,
        long totalRent,
        long totalDiscount,
        long totalLateFee,
        long totalDamage
) {}
