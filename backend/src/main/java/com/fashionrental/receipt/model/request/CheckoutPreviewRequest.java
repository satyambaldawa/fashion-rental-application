package com.fashionrental.receipt.model.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.List;

public record CheckoutPreviewRequest(
        @NotNull OffsetDateTime startDatetime,
        @NotNull OffsetDateTime endDatetime,
        @Valid List<CheckoutLineItem> items,
        List<@NotNull @Valid AdHocLineItem> adHocItems
) {
    public CheckoutPreviewRequest {
        items = items == null ? List.of() : items;
        adHocItems = adHocItems == null ? List.of() : adHocItems;
    }

    @JsonIgnore
    @AssertTrue(message = "A preview needs at least one item")
    public boolean isAtLeastOneLinePresent() {
        return !items.isEmpty() || !adHocItems.isEmpty();
    }
}
