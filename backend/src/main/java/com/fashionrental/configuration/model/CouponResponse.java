package com.fashionrental.configuration.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CouponResponse(
        UUID id,
        String code,
        String discountType,
        int value,
        Integer minSubtotal,
        OffsetDateTime validFrom,
        OffsetDateTime validTo,
        Integer usageLimit,
        int timesUsed,
        boolean isActive,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
