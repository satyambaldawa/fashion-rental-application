package com.fashionrental.reporting.model.response;

import java.util.List;

public record OverdueRentalsResponse(
        int overdueCount,
        List<OverdueRentalItem> items
) {}
