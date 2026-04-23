package com.fashionrental.invoice.model.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.List;

public record ProcessReturnRequest(
        @NotNull OffsetDateTime returnDatetime,
        @NotNull @Valid List<ReturnLineItem> lineItems,
        String paymentMethod,  // CASH | UPI | OTHER — defaults to CASH if null
        String damageNotes,
        String notes
) {}
