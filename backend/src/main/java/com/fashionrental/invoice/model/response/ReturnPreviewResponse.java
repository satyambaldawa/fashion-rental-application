package com.fashionrental.invoice.model.response;

import java.util.List;

public record ReturnPreviewResponse(
        int calculatedLateFee,   // fee from the rules, before the decision
        int totalLateFee,        // applied fee (0 when waived); name unchanged for existing UI/JSON
        int waivedLateFee,
        int totalDamageCost,
        int totalDeductions,
        int depositToReturn,
        int finalAmount,
        String transactionType,  // COLLECT | REFUND
        List<ReturnPreviewLineItem> lineItems
) {}
