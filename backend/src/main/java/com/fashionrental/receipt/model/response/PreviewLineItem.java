package com.fashionrental.receipt.model.response;

import java.util.UUID;

public record PreviewLineItem(
        UUID itemId,
        String itemName,
        int rate,
        int deposit,
        int quantity,
        int rentalDays,
        int lineRent,
        int lineDeposit,
        int availableQuantity
) {}
