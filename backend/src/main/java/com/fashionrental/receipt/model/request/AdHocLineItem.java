package com.fashionrental.receipt.model.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdHocLineItem(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 50) String size,
        // Total rent for the whole rental period, per unit — not a per-day rate like Item.rate
        @NotNull @Min(1) Integer flatPrice,
        @NotNull @Min(0) Integer deposit,
        @NotNull @Min(1) Integer quantity
) {}
