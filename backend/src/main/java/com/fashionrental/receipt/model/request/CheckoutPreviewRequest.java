package com.fashionrental.receipt.model.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.List;

public record CheckoutPreviewRequest(
        @NotNull OffsetDateTime startDatetime,
        @NotNull OffsetDateTime endDatetime,
        @NotEmpty @Valid List<CheckoutLineItem> items
) {}
