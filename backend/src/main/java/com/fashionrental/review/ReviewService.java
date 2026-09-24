package com.fashionrental.review;

import com.fashionrental.common.exception.ResourceNotFoundException;
import com.fashionrental.inventory.storage.ImageStorageService;
import com.fashionrental.inventory.storage.UploadResult;
import com.fashionrental.review.model.ReviewSort;
import com.fashionrental.review.model.request.SubmitReviewRequest;
import com.fashionrental.review.model.response.AdminReviewResponse;
import com.fashionrental.review.model.response.PublicReviewResponse;
import com.fashionrental.review.model.response.SubmitReviewResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ReviewService {

    private static final int PUBLIC_PAGE_SIZE = 10;

    private final ReviewRepository reviewRepository;
    private final ReviewSubmissionGuard reviewSubmissionGuard;
    private final ReviewImageUploader reviewImageUploader;
    private final ImageStorageService imageStorageService;
    private final Clock clock;

    public ReviewService(ReviewRepository reviewRepository,
                          ReviewSubmissionGuard reviewSubmissionGuard,
                          ReviewImageUploader reviewImageUploader,
                          ImageStorageService imageStorageService,
                          Clock clock) {
        this.reviewRepository = reviewRepository;
        this.reviewSubmissionGuard = reviewSubmissionGuard;
        this.reviewImageUploader = reviewImageUploader;
        this.imageStorageService = imageStorageService;
        this.clock = clock;
    }

    // Intentionally NOT @Transactional. reviewRepository.save() below runs in its own
    // repository-managed transaction and commits before returning, so a DB failure (e.g. a
    // constraint violation) is caught here and triggers deleteAll on the uploaded R2 objects.
    // Wrapping this method in an outer transaction would defer the INSERT to commit time, after
    // this method has already returned — outside this catch — orphaning the uploaded objects.
    public SubmitReviewResponse submit(SubmitReviewRequest request, MultipartFile[] images, String submitterIp) {
        reviewSubmissionGuard.checkSubmissionAllowed(submitterIp, request.phone());

        List<UploadResult> uploaded = reviewImageUploader.uploadAll(images);
        try {
            Review review = buildPendingReview(request, submitterIp, uploaded);
            return ReviewMapper.toSubmitResponse(reviewRepository.save(review));
        } catch (RuntimeException e) {
            reviewImageUploader.deleteAll(uploaded);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public Page<PublicReviewResponse> listPublic(ReviewSort sort, int page) {
        Pageable pageable = PageRequest.of(page, PUBLIC_PAGE_SIZE, sortFor(sort));
        return reviewRepository.findByStatus(Review.Status.APPROVED, pageable)
                .map(ReviewMapper::toPublicResponse);
    }

    @Transactional(readOnly = true)
    public Page<AdminReviewResponse> listForModeration(Review.Status status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, moderationSort());
        Page<Review> reviews = status == null
                ? reviewRepository.findAll(pageable)
                : reviewRepository.findByStatus(status, pageable);
        return reviews.map(ReviewMapper::toAdminResponse);
    }

    @Transactional
    public AdminReviewResponse updateStatus(UUID id, Review.Status status) {
        Review review = findOrThrow(id);

        // Rejected reviews lose their images permanently — re-approving a previously-rejected
        // review is not a supported workflow the UI offers, and showing an image-less review is
        // preferable to a dead URL.
        if (status == Review.Status.REJECTED) {
            review.getImages().forEach(image ->
                    imageStorageService.deleteImage(image.getUrl(), image.getThumbnailUrl()));
            review.getImages().clear();
        }

        review.setStatus(status);
        review.setModeratedAt(OffsetDateTime.now(clock));
        return ReviewMapper.toAdminResponse(reviewRepository.save(review));
    }

    @Transactional
    public void delete(UUID id) {
        Review review = findOrThrow(id);
        review.getImages().forEach(image -> imageStorageService.deleteImage(image.getUrl(), image.getThumbnailUrl()));
        reviewRepository.delete(review);
    }

    private Review findOrThrow(UUID id) {
        return reviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found: " + id));
    }

    private Review buildPendingReview(SubmitReviewRequest request, String submitterIp, List<UploadResult> uploaded) {
        Review review = new Review();
        review.setReviewerName(request.reviewerName());
        review.setPhone(request.phone());
        review.setItemDescription(request.itemDescription());
        review.setRating(request.rating());
        review.setReviewText(request.reviewText());
        review.setStatus(Review.Status.PENDING);
        review.setSubmitterIp(submitterIp);

        for (int i = 0; i < uploaded.size(); i++) {
            ReviewImage image = new ReviewImage();
            image.setUrl(uploaded.get(i).fullUrl());
            image.setThumbnailUrl(uploaded.get(i).thumbnailUrl());
            image.setSortOrder(i);
            review.addImage(image);
        }
        return review;
    }

    private static Sort sortFor(ReviewSort sort) {
        return sort == ReviewSort.HIGHEST_RATED
                ? Sort.by(Sort.Direction.DESC, "rating")
                        .and(Sort.by(Sort.Direction.DESC, "createdAt"))
                        .and(Sort.by(Sort.Direction.DESC, "id"))
                : Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));
    }

    private static Sort moderationSort() {
        return Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));
    }
}
