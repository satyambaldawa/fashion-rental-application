package com.fashionrental.review.model.response;

import com.fashionrental.review.Review;

import java.util.UUID;

public record SubmitReviewResponse(UUID id, Review.Status status) {}
