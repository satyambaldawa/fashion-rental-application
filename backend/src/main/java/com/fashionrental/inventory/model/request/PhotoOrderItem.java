package com.fashionrental.inventory.model.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PhotoOrderItem(
        @NotNull UUID id,
        @NotNull Integer sortOrder
) {}
