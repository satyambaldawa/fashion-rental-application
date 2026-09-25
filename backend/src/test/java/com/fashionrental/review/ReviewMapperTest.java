package com.fashionrental.review;

import com.fashionrental.review.model.response.PublicReviewResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewMapperTest {

    @Test
    void should_omit_phone_from_the_public_response_type() {
        String[] components = Arrays.stream(PublicReviewResponse.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toArray(String[]::new);

        assertThat(components).doesNotContain("phone", "submitterIp", "status");
    }

    @Test
    void should_map_every_public_field_from_the_entity() {
        Review review = ReviewTestFixtures.approvedReview();

        PublicReviewResponse response = ReviewMapper.toPublicResponse(review);

        assertThat(response.reviewerName()).isEqualTo("Priya S");
        assertThat(response.itemDescription()).isEqualTo("Red bridal lehenga");
        assertThat(response.rating()).isEqualTo(5);
        assertThat(response.reviewText()).isEqualTo("Beautiful outfit, fit perfectly.");
        assertThat(response.images()).hasSize(1);
        assertThat(response.images().getFirst().thumbnailUrl()).isEqualTo("thumb-1");
    }

    @Test
    void should_include_phone_in_the_admin_response() {
        Review review = ReviewTestFixtures.approvedReview();

        assertThat(ReviewMapper.toAdminResponse(review).phone()).isEqualTo("9876543210");
    }
}
