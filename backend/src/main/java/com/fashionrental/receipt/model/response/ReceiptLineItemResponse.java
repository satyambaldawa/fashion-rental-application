package com.fashionrental.receipt.model.response;

import java.util.UUID;

public record ReceiptLineItemResponse(
        UUID id,
        UUID itemId,
        String itemName,
        String itemSize,
        String itemCategory,
        String itemDescription,
        int quantity,
        int rateSnapshot,
        int depositSnapshot,
        int lineRent,
        int lineDeposit,
        Integer itemPurchaseRate  // null if not recorded; used to gate damage-by-percentage
) {}
