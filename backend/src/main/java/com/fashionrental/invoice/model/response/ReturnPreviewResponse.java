package com.fashionrental.invoice.model.response;

import java.util.List;

public record ReturnPreviewResponse(
        int totalLateFee,
        int totalDamageCost,
        int totalDeductions,
        int depositToReturn,
        int finalAmount,
        String transactionType,  // COLLECT | REFUND
        List<ReturnPreviewLineItem> lineItems
) {}
