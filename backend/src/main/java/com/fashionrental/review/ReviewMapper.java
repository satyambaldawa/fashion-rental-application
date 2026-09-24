package com.fashionrental.review;

import com.fashionrental.review.model.response.AdminReviewResponse;
import com.fashionrental.review.model.response.PublicReviewResponse;
import com.fashionrental.review.model.response.ReviewImageResponse;
import com.fashionrental.review.model.response.SubmitReviewResponse;

import java.util.List;

public final class ReviewMapper {

    private ReviewMapper() {
    }

    public static PublicReviewResponse toPublicResponse(Review review) {
        return new PublicReviewResponse(
                review.getId(),
                review.getReviewerName(),
                review.getItemDescription(),
                review.getRating(),
                review.getReviewText(),
                review.getCreatedAt(),
                toImageResponses(review));
    }

    public static AdminReviewResponse toAdminResponse(Review review) {
        return new AdminReviewResponse(
                review.getId(),
                review.getReviewerName(),
                review.getPhone(),
                review.getItemDescription(),
                review.getRating(),
                review.getReviewText(),
                review.getStatus(),
                review.getCreatedAt(),
                review.getModeratedAt(),
                toImageResponses(review));
    }

    public static SubmitReviewResponse toSubmitResponse(Review review) {
        return new SubmitReviewResponse(review.getId(), review.getStatus());
    }

    private static List<ReviewImageResponse> toImageResponses(Review review) {
        return review.getImages().stream()
                .map(image -> new ReviewImageResponse(image.getId(), image.getUrl(), image.getThumbnailUrl()))
                .toList();
    }
}
