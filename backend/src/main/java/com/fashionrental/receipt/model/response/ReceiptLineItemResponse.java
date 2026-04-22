package com.fashionrental.receipt.model.response;

import java.util.UUID;

public record ReceiptLineItemResponse(
        UUID id,
        UUID itemId,
        String itemName,
        int quantity,
        int rateSnapshot,
        int depositSnapshot,
        int lineRent,
        int lineDeposit
) {}
