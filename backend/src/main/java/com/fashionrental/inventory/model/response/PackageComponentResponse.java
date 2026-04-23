package com.fashionrental.inventory.model.response;

import com.fashionrental.inventory.Item;

import java.util.List;
import java.util.UUID;

public record PackageComponentResponse(
        UUID componentItemId,
        String componentItemName,
        Item.Category componentItemCategory,
        String componentItemSize,
        String componentItemDescription,
        List<ItemPhotoResponse> componentItemPhotos,
        int quantity
) {}
