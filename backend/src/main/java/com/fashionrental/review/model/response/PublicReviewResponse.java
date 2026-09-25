package com.fashionrental.review.model.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record PublicReviewResponse(
        UUID id,
        String reviewerName,
        String itemDescription,
        int rating,
        String reviewText,
        OffsetDateTime createdAt,
        List<ReviewImageResponse> images
) {}
