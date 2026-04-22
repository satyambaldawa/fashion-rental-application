package com.fashionrental.receipt.model.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CheckoutLineItem(
        @NotNull UUID itemId,
        @NotNull @Min(1) Integer quantity
) {}
