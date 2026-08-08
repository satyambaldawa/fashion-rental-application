package com.fashionrental.customer.model.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record CustomerReceiptResponse(
        UUID id,
        String receiptNumber,
        String status,
        OffsetDateTime startDatetime,
        OffsetDateTime endDatetime,
        int totalRent,
        int totalDeposit,
        int grandTotal,
        List<CustomerReceiptItemResponse> items,
        CustomerReceiptInvoiceResponse invoice
) {}
