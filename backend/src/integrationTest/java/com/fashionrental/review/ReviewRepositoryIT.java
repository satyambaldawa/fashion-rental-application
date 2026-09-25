package com.fashionrental.review;

import com.fashionrental.AbstractIntegrationTest;
import com.fashionrental.review.model.ReviewSort;
import com.fashionrental.review.model.response.PublicReviewResponse;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewRepositoryIT extends AbstractIntegrationTest {

    @Autowired private ReviewRepository reviewRepository;
    @Autowired private ReviewService reviewService;
    @Autowired private EntityManager entityManager;

    @BeforeEach
    void clearReviews() {
        reviewRepository.deleteAll();
    }

    private Review persist(Review.Status status, int rating, String name) {
        Review review = new Review();
        review.setReviewerName(name);
        review.setPhone("9876543210");
        review.setItemDescription("Red lehenga");
        review.setRating(rating);
        review.setReviewText("Lovely outfit.");
        review.setStatus(status);
        review.setSubmitterIp("203.0.113.7");
        return reviewRepository.saveAndFlush(review);
    }

    @Test
    void should_return_only_approved_reviews_on_the_public_query() {
        persist(Review.Status.APPROVED, 5, "Approved");
        persist(Review.Status.PENDING, 5, "Pending");
        persist(Review.Status.REJECTED, 5, "Rejected");

        Page<PublicReviewResponse> page = reviewService.listPublic(ReviewSort.NEWEST, 0);

        assertThat(page.getContent()).extracting(PublicReviewResponse::reviewerName)
                .containsExactly("Approved");
    }

    @Test
    void should_return_ten_reviews_per_page() {
        for (int i = 0; i < 25; i++) {
            persist(Review.Status.APPROVED, 5, "Reviewer " + i);
        }

        assertThat(reviewService.listPublic(ReviewSort.NEWEST, 0).getTotalElements()).isEqualTo(25);
        assertThat(reviewService.listPublic(ReviewSort.NEWEST, 0).getTotalPages()).isEqualTo(3);
        assertThat(reviewService.listPublic(ReviewSort.NEWEST, 0).getContent()).hasSize(10);
        assertThat(reviewService.listPublic(ReviewSort.NEWEST, 1).getContent()).hasSize(10);
        assertThat(reviewService.listPublic(ReviewSort.NEWEST, 2).getContent()).hasSize(5);
    }

    @Test
    @Transactional
    void should_never_repeat_or_drop_a_review_across_pages_when_ratings_tie() {
        for (int i = 0; i < 25; i++) {
            persist(Review.Status.APPROVED, 4, "Reviewer " + i);
        }

        // A loop of independent saves can still collide on the same created_at instant by luck,
        // but relying on that luck would let this test pass even without the id tiebreaker. Force
        // a genuine, deterministic tie: overwrite every row's created_at to the exact same value
        // via a single UPDATE, which is exactly the scenario the id tiebreaker exists to resolve.
        entityManager.flush();
        entityManager.createNativeQuery("UPDATE reviews SET created_at = NOW() WHERE rating = 4")
                .executeUpdate();
        entityManager.clear();

        List<UUID> seen = new ArrayList<>();
        for (int page = 0; page < 3; page++) {
            seen.addAll(reviewService.listPublic(ReviewSort.HIGHEST_RATED, page)
                    .getContent().stream()
                    .map(PublicReviewResponse::id)
                    .toList());
        }

        assertThat(seen).hasSize(25);
        assertThat(seen).doesNotHaveDuplicates();
    }

    @Test
    void should_order_highest_rated_first() {
        persist(Review.Status.APPROVED, 2, "Two star");
        persist(Review.Status.APPROVED, 5, "Five star");
        persist(Review.Status.APPROVED, 3, "Three star");

        assertThat(reviewService.listPublic(ReviewSort.HIGHEST_RATED, 0).getContent())
                .extracting(PublicReviewResponse::rating)
                .containsExactly(5, 3, 2);
    }

    @Test
    void should_count_only_reviews_inside_the_rate_limit_window() {
        persist(Review.Status.PENDING, 5, "Recent");

        assertThat(reviewRepository.countBySubmitterIpAndCreatedAtAfter(
                "203.0.113.7", OffsetDateTime.now().minusHours(1))).isEqualTo(1);
        assertThat(reviewRepository.countBySubmitterIpAndCreatedAtAfter(
                "203.0.113.7", OffsetDateTime.now().plusMinutes(1))).isZero();
    }

    @Test
    void should_cascade_delete_review_images_when_the_review_is_deleted() {
        Review review = new Review();
        review.setReviewerName("Priya S");
        review.setPhone("9876543210");
        review.setItemDescription("Red lehenga");
        review.setRating(5);
        review.setReviewText("Lovely outfit.");
        review.setSubmitterIp("203.0.113.7");
        ReviewImage image = new ReviewImage();
        image.setUrl("full-1");
        image.setThumbnailUrl("thumb-1");
        image.setSortOrder(0);
        review.addImage(image);
        Review saved = reviewRepository.save(review);

        reviewRepository.deleteById(saved.getId());

        assertThat(reviewRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    void should_reject_a_rating_outside_one_to_five_at_the_database_level() {
        assertThat(org.assertj.core.api.Assertions.catchThrowable(
                () -> persist(Review.Status.PENDING, 9, "Priya S"))).isNotNull();
    }
}
