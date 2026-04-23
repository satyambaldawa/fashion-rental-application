package com.fashionrental.invoice.model.response;

import java.util.UUID;

public record ReturnPreviewLineItem(
        UUID receiptLineItemId,
        String itemName,
        int quantity,
        int lateFee,
        int damageCost
) {}
