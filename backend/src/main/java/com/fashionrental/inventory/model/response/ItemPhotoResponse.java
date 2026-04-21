package com.fashionrental.inventory.model.response;

import java.util.UUID;

public record ItemPhotoResponse(
        UUID id,
        String url,
        String thumbnailUrl,
        int sortOrder
) {}
