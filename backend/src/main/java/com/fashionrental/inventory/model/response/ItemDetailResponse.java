package com.fashionrental.inventory.model.response;

import com.fashionrental.inventory.Item;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ItemDetailResponse(
        UUID id,
        String name,
        Item.Category category,
        Item.ItemType itemType,
        String size,
        String description,
        int rate,
        int deposit,
        int quantity,
        boolean isActive,
        String notes,
        // Internal purchase tracking — visible to shop owner only, never shown to customers
        Integer purchaseRate,
        String vendorName,
        List<ItemPhotoResponse> photos,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
