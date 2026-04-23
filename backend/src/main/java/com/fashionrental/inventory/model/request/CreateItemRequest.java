package com.fashionrental.inventory.model.request;

import com.fashionrental.inventory.Item;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateItemRequest(
        @NotBlank String name,
        @NotNull Item.Category category,
        @NotNull Item.ItemType itemType,
        String size,
        String description,
        @NotNull @Min(1) Integer rate,
        @NotNull @Min(0) Integer deposit,
        @NotNull @Min(1) Integer quantity,
        String notes,
        // Internal purchase tracking — not returned in customer-facing responses
        @Min(0) Integer purchaseRate,
        String vendorName,
        // Required when itemType = PACKAGE; must be null or empty for INDIVIDUAL
        List<PackageComponentRequest> components
) {}
