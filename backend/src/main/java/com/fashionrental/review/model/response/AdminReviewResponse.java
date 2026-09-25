package com.fashionrental.review.model.response;

import com.fashionrental.review.Review;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AdminReviewResponse(
        UUID id,
        String reviewerName,
        String phone,
        String itemDescription,
        int rating,
        String reviewText,
        Review.Status status,
        OffsetDateTime createdAt,
        OffsetDateTime moderatedAt,
        List<ReviewImageResponse> images
) {}
