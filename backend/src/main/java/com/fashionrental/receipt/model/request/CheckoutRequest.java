package com.fashionrental.receipt.model.request;

import com.fashionrental.receipt.ReceiptPaymentMethod;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record CheckoutRequest(
        @NotNull UUID customerId,
        @NotNull OffsetDateTime startDatetime,
        @NotNull OffsetDateTime endDatetime,
        @Valid List<CheckoutLineItem> items,
        List<@NotNull @Valid AdHocLineItem> adHocItems,
        String notes,
        @Size(max = 32) String couponCode,
        ReceiptPaymentMethod paymentMethod,
        // The amount the UPI QR asked the customer to pay. Required for UPI so a receipt is
        // never created for a different total than the customer already paid.
        @Min(0) Integer expectedGrandTotal,
        // One key per UPI confirmation; replaying it returns the receipt already created.
        UUID idempotencyKey
) {
    // JSON that omits either key binds null; without this the guard below would NPE.
    // A missing paymentMethod means CASH: a PWA cached on the tablet from before UPI
    // existed must keep checking out exactly as it did.
    public CheckoutRequest {
        items = items == null ? List.of() : items;
        adHocItems = adHocItems == null ? List.of() : adHocItems;
        paymentMethod = paymentMethod == null ? ReceiptPaymentMethod.CASH : paymentMethod;
    }

    @JsonIgnore
    @AssertTrue(message = "UPI checkout must include the amount shown in the QR")
    public boolean isExpectedGrandTotalPresentForUpi() {
        return paymentMethod != ReceiptPaymentMethod.UPI || expectedGrandTotal != null;
    }

    // Without a key, retrying "received" after a lost response would create a second receipt
    // for a payment that was already collected once.
    @JsonIgnore
    @AssertTrue(message = "UPI checkout must include an idempotency key")
    public boolean isIdempotencyKeyPresentForUpi() {
        return paymentMethod != ReceiptPaymentMethod.UPI || idempotencyKey != null;
    }

    // Jackson would otherwise serialise this as a request property and springdoc would publish it
    @JsonIgnore
    @AssertTrue(message = "A receipt needs at least one item")
    public boolean isAtLeastOneLinePresent() {
        return !items.isEmpty() || !adHocItems.isEmpty();
    }
}
