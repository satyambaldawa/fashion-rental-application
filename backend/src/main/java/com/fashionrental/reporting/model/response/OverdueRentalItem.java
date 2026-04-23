package com.fashionrental.reporting.model.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record OverdueRentalItem(
        UUID receiptId,
        String receiptNumber,
        String customerName,
        String customerPhone,
        List<String> itemNames,
        OffsetDateTime endDatetime,
        double overdueHours
) {}
