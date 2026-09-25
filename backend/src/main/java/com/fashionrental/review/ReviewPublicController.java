package com.fashionrental.review;

import com.fashionrental.common.response.ApiResponse;
import com.fashionrental.common.util.ClientIpResolver;
import com.fashionrental.review.model.ReviewSort;
import com.fashionrental.review.model.request.SubmitReviewRequest;
import com.fashionrental.review.model.response.PublicReviewResponse;
import com.fashionrental.review.model.response.SubmitReviewResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Reviews", description = "Public review submission and browsing")
@RestController
@RequestMapping("/api/public/reviews")
public class ReviewPublicController {

    private final ReviewService reviewService;

    public ReviewPublicController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @Operation(summary = "Submit a review with up to three photos; lands as PENDING")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<SubmitReviewResponse>> submitReview(
            @Valid @RequestPart("review") SubmitReviewRequest request,
            @RequestPart(value = "images", required = false) MultipartFile[] images,
            HttpServletRequest httpRequest
    ) {
        String submitterIp = ClientIpResolver.resolve(httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(reviewService.submit(request, images, submitterIp)));
    }

    @Operation(summary = "List approved reviews, ten per page")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<PublicReviewResponse>>> listReviews(
            @RequestParam(defaultValue = "NEWEST") ReviewSort sort,
            @RequestParam(defaultValue = "0") int page
    ) {
        return ResponseEntity.ok(ApiResponse.ok(reviewService.listPublic(sort, page)));
    }
}
