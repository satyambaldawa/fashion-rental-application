package com.fashionrental.invoice.model.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record InvoiceResponse(
        UUID id,
        String invoiceNumber,
        UUID receiptId,
        String receiptNumber,
        UUID customerId,
        String customerName,
        String customerPhone,
        OffsetDateTime returnDatetime,
        int totalRent,
        int totalDepositCollected,
        int totalLateFee,
        int totalDamageCost,
        int depositToReturn,
        int finalAmount,
        String transactionType,  // COLLECT | REFUND
        String paymentMethod,
        String damageNotes,
        String notes,
        List<InvoiceLineItemResponse> lineItems,
        OffsetDateTime createdAt
) {}
