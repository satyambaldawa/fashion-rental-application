package com.fashionrental.customer.model.response;

import java.util.UUID;

public record CustomerReceiptInvoiceResponse(
        UUID id,
        String invoiceNumber,
        int finalAmount,
        String transactionType
) {}
