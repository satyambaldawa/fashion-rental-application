package com.fashionrental.reporting.model.response;

import java.util.List;

public record OutstandingDepositsResponse(
        int totalOutstanding,
        List<OutstandingDepositItem> items
) {}
