package com.fashionrental.inventory.model.request;

import com.fashionrental.inventory.Item;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateItemRequest(
        @NotBlank String name,
        @NotNull Item.Category category,
        String size,
        String description,
        @NotNull @Min(1) Integer rate,
        @NotNull @Min(0) Integer deposit,
        @NotNull @Min(1) Integer quantity,
        String notes
) {}
