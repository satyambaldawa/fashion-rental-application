package com.fashionrental.receipt.model.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ReceiptSummaryResponse(
        UUID id,
        String receiptNumber,
        String customerName,
        String customerPhone,
        List<String> itemNames,
        OffsetDateTime startDatetime,
        OffsetDateTime endDatetime,
        int rentalDays,
        int totalRent,
        String couponCode,
        int discountAmount,
        int totalDeposit,
        int grandTotal,
        String status,
        boolean isOverdue,
        Double overdueHours
) {}
