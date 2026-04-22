package com.fashionrental.receipt.model.response;

import java.util.List;

public record CheckoutPreviewResponse(
        boolean allAvailable,
        List<PreviewLineItem> lineItems,
        int rentalDays,
        int totalRent,
        int totalDeposit,
        int grandTotal,
        List<String> unavailableItems
) {}
