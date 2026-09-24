package com.fashionrental.review;

import com.fashionrental.common.exception.ResourceNotFoundException;
import com.fashionrental.inventory.storage.ImageStorageService;
import com.fashionrental.inventory.storage.UploadResult;
import com.fashionrental.review.model.ReviewSort;
import com.fashionrental.review.model.request.SubmitReviewRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    private static final String IP = "203.0.113.7";
    private static final Instant NOW = Instant.parse("2026-09-21T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock private ReviewRepository reviewRepository;
    @Mock private ReviewSubmissionGuard reviewSubmissionGuard;
    @Mock private ReviewImageUploader reviewImageUploader;
    @Mock private ImageStorageService imageStorageService;

    private ReviewService reviewService;

    @BeforeEach
    void setUp() {
        reviewService = new ReviewService(
                reviewRepository, reviewSubmissionGuard, reviewImageUploader, imageStorageService, CLOCK);
    }

    private static SubmitReviewRequest request() {
        return new SubmitReviewRequest("Priya S", "9876543210", "Red lehenga", 5, "Lovely outfit.");
    }

    @Test
    void should_persist_a_new_review_with_pending_status() {
        when(reviewImageUploader.uploadAll(any())).thenReturn(List.of());
        when(reviewRepository.save(any(Review.class))).thenAnswer(i -> i.getArgument(0));

        reviewService.submit(request(), null, IP);

        ArgumentCaptor<Review> saved = ArgumentCaptor.forClass(Review.class);
        verify(reviewRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(Review.Status.PENDING);
        assertThat(saved.getValue().getSubmitterIp()).isEqualTo(IP);
        assertThat(saved.getValue().getPhone()).isEqualTo("9876543210");
    }

    @Test
    void should_check_the_rate_limit_before_uploading_anything() {
        doThrow(new RuntimeException("limited")).when(reviewSubmissionGuard)
                .checkSubmissionAllowed(IP, "9876543210");

        assertThatThrownBy(() -> reviewService.submit(request(), null, IP));

        verify(reviewImageUploader, never()).uploadAll(any());
    }

    @Test
    void should_attach_uploaded_images_in_submission_order() {
        when(reviewImageUploader.uploadAll(any()))
                .thenReturn(List.of(new UploadResult("f1", "t1"), new UploadResult("f2", "t2")));
        when(reviewRepository.save(any(Review.class))).thenAnswer(i -> i.getArgument(0));

        reviewService.submit(request(), null, IP);

        ArgumentCaptor<Review> saved = ArgumentCaptor.forClass(Review.class);
        verify(reviewRepository).save(saved.capture());
        assertThat(saved.getValue().getImages())
                .extracting(ReviewImage::getUrl, ReviewImage::getSortOrder)
                .containsExactly(tuple("f1", 0), tuple("f2", 1));
    }

    @Test
    void should_delete_uploaded_images_when_persisting_the_review_fails() {
        List<UploadResult> uploaded = List.of(new UploadResult("f1", "t1"));
        when(reviewImageUploader.uploadAll(any())).thenReturn(uploaded);
        when(reviewRepository.save(any(Review.class))).thenThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> reviewService.submit(request(), null, IP));

        verify(reviewImageUploader).deleteAll(uploaded);
    }

    @Test
    void should_delete_uploaded_images_when_the_repository_save_itself_throws() {
        List<UploadResult> uploaded = List.of(new UploadResult("f1", "t1"));
        when(reviewImageUploader.uploadAll(any())).thenReturn(uploaded);
        when(reviewRepository.save(any(Review.class)))
                .thenThrow(new DataIntegrityViolationException("constraint violation"));

        assertThatThrownBy(() -> reviewService.submit(request(), null, IP))
                .isInstanceOf(DataIntegrityViolationException.class);

        verify(reviewImageUploader).deleteAll(uploaded);
    }

    @Test
    void should_request_ten_approved_reviews_sorted_by_newest() {
        when(reviewRepository.findByStatus(eq(Review.Status.APPROVED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        reviewService.listPublic(ReviewSort.NEWEST, 0);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(reviewRepository).findByStatus(eq(Review.Status.APPROVED), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(10);
        assertThat(pageable.getValue().getSort()).isEqualTo(
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
    }

    @Test
    void should_break_rating_ties_with_created_at_when_sorting_by_highest_rated() {
        when(reviewRepository.findByStatus(eq(Review.Status.APPROVED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        reviewService.listPublic(ReviewSort.HIGHEST_RATED, 0);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(reviewRepository).findByStatus(eq(Review.Status.APPROVED), pageable.capture());
        assertThat(pageable.getValue().getSort()).isEqualTo(
                Sort.by(Sort.Direction.DESC, "rating")
                        .and(Sort.by(Sort.Direction.DESC, "createdAt"))
                        .and(Sort.by(Sort.Direction.DESC, "id")));
    }

    @Test
    void should_stamp_moderated_at_when_status_changes() {
        Review review = ReviewTestFixtures.approvedReview();
        UUID id = UUID.randomUUID();
        when(reviewRepository.findById(id)).thenReturn(Optional.of(review));
        when(reviewRepository.save(any(Review.class))).thenAnswer(i -> i.getArgument(0));

        reviewService.updateStatus(id, Review.Status.REJECTED);

        assertThat(review.getStatus()).isEqualTo(Review.Status.REJECTED);
        assertThat(review.getModeratedAt()).isEqualTo(java.time.OffsetDateTime.now(CLOCK));
    }

    @Test
    void should_throw_when_moderating_a_review_that_does_not_exist() {
        UUID id = UUID.randomUUID();
        when(reviewRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.updateStatus(id, Review.Status.APPROVED))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void should_delete_stored_objects_before_deleting_the_review() {
        Review review = ReviewTestFixtures.approvedReview();
        UUID id = UUID.randomUUID();
        when(reviewRepository.findById(id)).thenReturn(Optional.of(review));

        reviewService.delete(id);

        InOrder inOrder = inOrder(imageStorageService, reviewRepository);
        inOrder.verify(imageStorageService).deleteImage("full-1", "thumb-1");
        inOrder.verify(reviewRepository).delete(review);
    }

    @Test
    void should_delete_stored_images_when_status_changes_to_rejected() {
        Review review = new Review();
        review.setReviewerName("Priya S");
        review.setPhone("9876543210");
        review.setItemDescription("Red lehenga");
        review.setRating(5);
        review.setReviewText("Lovely outfit.");
        review.setStatus(Review.Status.PENDING);
        review.setSubmitterIp(IP);

        ReviewImage image1 = new ReviewImage();
        image1.setUrl("full-1");
        image1.setThumbnailUrl("thumb-1");
        review.addImage(image1);

        ReviewImage image2 = new ReviewImage();
        image2.setUrl("full-2");
        image2.setThumbnailUrl("thumb-2");
        review.addImage(image2);

        UUID id = UUID.randomUUID();
        when(reviewRepository.findById(id)).thenReturn(Optional.of(review));
        when(reviewRepository.save(any(Review.class))).thenAnswer(i -> i.getArgument(0));

        reviewService.updateStatus(id, Review.Status.REJECTED);

        verify(imageStorageService).deleteImage("full-1", "thumb-1");
        verify(imageStorageService).deleteImage("full-2", "thumb-2");
        assertThat(review.getImages()).isEmpty();
    }

    @Test
    void should_request_the_moderation_queue_with_a_stable_default_sort() {
        when(reviewRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        reviewService.listForModeration(null, 0, 20);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(reviewRepository).findAll(pageable.capture());
        assertThat(pageable.getValue().getSort()).isEqualTo(
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
    }

    @Test
    void should_filter_the_moderation_queue_by_status_when_given() {
        when(reviewRepository.findByStatus(eq(Review.Status.PENDING), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        reviewService.listForModeration(Review.Status.PENDING, 0, 20);

        verify(reviewRepository).findByStatus(eq(Review.Status.PENDING), any(Pageable.class));
        verify(reviewRepository, never()).findAll(any(Pageable.class));
    }
}
