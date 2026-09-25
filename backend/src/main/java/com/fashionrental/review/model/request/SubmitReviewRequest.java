package com.fashionrental.review.model.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SubmitReviewRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 80, message = "Name must be 80 characters or fewer")
        String reviewerName,

        @NotBlank(message = "Mobile number is required")
        @Pattern(regexp = "^[6-9]\\d{9}$", message = "Enter a valid 10-digit Indian mobile number")
        String phone,

        @NotBlank(message = "Tell us what you rented")
        @Size(max = 100, message = "Keep this to 100 characters or fewer")
        String itemDescription,

        @NotNull(message = "Please give a rating")
        @Min(value = SubmitReviewRequest.MIN_RATING, message = SubmitReviewRequest.RATING_RANGE_MESSAGE)
        @Max(value = SubmitReviewRequest.MAX_RATING, message = SubmitReviewRequest.RATING_RANGE_MESSAGE)
        Integer rating,

        @NotBlank(message = "Review cannot be empty")
        @Size(max = 256, message = "Review must be 256 characters or fewer")
        String reviewText
) {
    static final int MIN_RATING = 1;
    static final int MAX_RATING = 5;
    static final String RATING_RANGE_MESSAGE =
            "Rating must be between " + MIN_RATING + " and " + MAX_RATING;
}
