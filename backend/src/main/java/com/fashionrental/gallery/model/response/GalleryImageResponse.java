package com.fashionrental.gallery.model.response;

import com.fashionrental.inventory.Item;

import java.util.UUID;

public record GalleryImageResponse(
        UUID id,
        Item.Category category,
        String imageUrl,
        String thumbnailUrl,
        String caption,
        boolean isActive
) {}
