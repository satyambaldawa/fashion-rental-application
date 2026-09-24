package com.fashionrental.review;

import com.fashionrental.common.response.ApiResponse;
import com.fashionrental.review.model.request.UpdateReviewStatusRequest;
import com.fashionrental.review.model.response.AdminReviewResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Review moderation", description = "Owner-only review moderation")
@RestController
@RequestMapping("/api/reviews")
public class ReviewAdminController {

    private final ReviewService reviewService;

    public ReviewAdminController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @Operation(summary = "List reviews for moderation, optionally filtered by status")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<AdminReviewResponse>>> listReviews(
            @RequestParam(required = false) Review.Status status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.ok(reviewService.listForModeration(status, page, size)));
    }

    @Operation(summary = "Approve or reject a review")
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<AdminReviewResponse>> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateReviewStatusRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(reviewService.updateStatus(id, request.status())));
    }

    @Operation(summary = "Delete a review and its stored photos")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteReview(@PathVariable UUID id) {
        reviewService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
