package com.fashionrental.reporting.model.response;

import java.time.OffsetDateTime;
import java.util.UUID;

public record InvoiceListItem(
        UUID invoiceId,
        String invoiceNumber,
        OffsetDateTime invoiceDate,
        String customerName,
        String receiptNumber,
        int items,
        int rent,
        int discount,
        int lateFee,
        int damage,
        int revenue
) {}
