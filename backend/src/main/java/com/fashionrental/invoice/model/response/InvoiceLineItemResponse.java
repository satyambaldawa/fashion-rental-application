package com.fashionrental.invoice.model.response;

import java.util.UUID;

public record InvoiceLineItemResponse(
        UUID id,
        UUID itemId,
        String itemName,
        String itemSize,
        String itemCategory,
        int quantityReturned,
        int rateSnapshot,
        int depositSnapshot,
        boolean isDamaged,
        Double damagePercentage,
        int damageCost,
        int lateFee
) {}
