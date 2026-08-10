package com.fashionrental.gallery.model.request;

import jakarta.validation.constraints.Size;

public record UpdateGalleryImageRequest(
        @Size(max = 2000) String caption,
        Boolean isActive
) {}
