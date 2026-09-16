package com.fashionrental.receipt.model.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record CheckoutRequest(
        @NotNull UUID customerId,
        @NotNull OffsetDateTime startDatetime,
        @NotNull OffsetDateTime endDatetime,
        @Valid List<CheckoutLineItem> items,
        List<@NotNull @Valid AdHocLineItem> adHocItems,
        String notes
) {
    // JSON that omits either key binds null; without this the guard below would NPE.
    public CheckoutRequest {
        items = items == null ? List.of() : items;
        adHocItems = adHocItems == null ? List.of() : adHocItems;
    }

    // Jackson would otherwise serialise this as a request property and springdoc would publish it
    @JsonIgnore
    @AssertTrue(message = "A receipt needs at least one item")
    public boolean isAtLeastOneLinePresent() {
        return !items.isEmpty() || !adHocItems.isEmpty();
    }
}
