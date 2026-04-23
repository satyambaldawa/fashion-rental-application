package com.fashionrental.inventory.model.response;

import com.fashionrental.inventory.Item;

import java.util.List;
import java.util.UUID;

public record ItemSummaryResponse(
        UUID id,
        String name,
        Item.Category category,
        Item.ItemType itemType,
        String size,
        String description,
        int rate,
        int deposit,
        int totalQuantity,
        int availableQuantity,
        boolean isAvailable,
        String thumbnailUrl,
        List<String> photoUrls,
        List<String> componentNames   // null for INDIVIDUAL, list of "ItemName ×qty" strings for PACKAGE
) {}
