package com.fashionrental.review.model.request;

import com.fashionrental.review.Review;
import jakarta.validation.constraints.NotNull;

public record UpdateReviewStatusRequest(@NotNull(message = "Status is required") Review.Status status) {}
