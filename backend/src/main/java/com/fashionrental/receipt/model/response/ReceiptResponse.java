package com.fashionrental.receipt.model.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ReceiptResponse(
        UUID id,
        String receiptNumber,
        UUID customerId,
        String customerName,
        String customerPhone,
        OffsetDateTime startDatetime,
        OffsetDateTime endDatetime,
        int rentalDays,
        int totalRent,
        int totalDeposit,
        int grandTotal,
        String status,
        String notes,
        List<ReceiptLineItemResponse> lineItems,
        OffsetDateTime createdAt
) {}
