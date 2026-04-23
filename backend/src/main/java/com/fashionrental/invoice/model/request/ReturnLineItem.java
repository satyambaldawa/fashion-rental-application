package com.fashionrental.invoice.model.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ReturnLineItem(
        @NotNull UUID receiptLineItemId,
        boolean isDamaged,
        Double damagePercentage,   // null if not damaged or using ad hoc
        Integer adHocDamageAmount  // null if using percentage; whole rupees
) {}
