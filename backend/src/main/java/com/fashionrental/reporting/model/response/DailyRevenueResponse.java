package com.fashionrental.reporting.model.response;

import java.time.LocalDate;

public record DailyRevenueResponse(
        LocalDate date,
        int rentCollected,
        int depositsCollected,
        int depositsRefunded,
        int collectedFromCustomers,
        int lateFeeIncome,
        int damageIncome,
        int netFlow,
        int newReceiptsCount,
        int returnsProcessedCount
) {}
