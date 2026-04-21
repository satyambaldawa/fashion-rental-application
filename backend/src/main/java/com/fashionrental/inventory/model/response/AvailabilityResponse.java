package com.fashionrental.inventory.model.response;

import java.util.UUID;

public record AvailabilityResponse(
        UUID itemId,
        int availableQuantity,
        boolean isAvailable
) {}
