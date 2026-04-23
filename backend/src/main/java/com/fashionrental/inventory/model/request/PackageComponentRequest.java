package com.fashionrental.inventory.model.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PackageComponentRequest(
        @NotNull UUID componentItemId,
        @NotNull @Min(1) int quantity
) {}
