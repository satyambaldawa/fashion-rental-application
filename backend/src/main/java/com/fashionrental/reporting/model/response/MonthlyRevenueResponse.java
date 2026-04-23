package com.fashionrental.reporting.model.response;

import java.util.List;

public record MonthlyRevenueResponse(
        int year,
        int month,
        int totalRentCollected,
        int totalDepositsCollected,
        int totalDepositsRefunded,
        int totalCollectedFromCustomers,
        int totalLateFeeIncome,
        int totalDamageIncome,
        int totalNetFlow,
        List<DailyRevenueSummary> dailyBreakdown
) {}
