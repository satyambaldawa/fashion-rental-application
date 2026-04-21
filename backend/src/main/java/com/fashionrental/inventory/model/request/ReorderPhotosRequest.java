package com.fashionrental.inventory.model.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ReorderPhotosRequest(
        @NotEmpty @Valid List<PhotoOrderItem> photos
) {}
