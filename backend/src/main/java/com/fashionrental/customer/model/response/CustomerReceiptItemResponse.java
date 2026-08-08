package com.fashionrental.customer.model.response;

public record CustomerReceiptItemResponse(
        String itemName,
        int quantity
) {}
