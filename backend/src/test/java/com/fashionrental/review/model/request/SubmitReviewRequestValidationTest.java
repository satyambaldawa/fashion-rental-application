package com.fashionrental.review.model.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SubmitReviewRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    private static SubmitReviewRequest valid() {
        return new SubmitReviewRequest("Priya S", "9876543210", "Red lehenga", 5, "Lovely outfit.");
    }

    @Test
    void should_accept_a_fully_valid_request() {
        assertThat(validator.validate(valid())).isEmpty();
    }

    @Test
    void should_reject_review_text_longer_than_256_characters() {
        SubmitReviewRequest request = new SubmitReviewRequest(
                "Priya S", "9876543210", "Red lehenga", 5, "x".repeat(257));

        assertThat(validator.validate(request))
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("reviewText");
    }

    @Test
    void should_accept_review_text_of_exactly_256_characters() {
        SubmitReviewRequest request = new SubmitReviewRequest(
                "Priya S", "9876543210", "Red lehenga", 5, "x".repeat(256));

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void should_reject_rating_below_one_and_above_five() {
        assertThat(validator.validate(
                new SubmitReviewRequest("Priya S", "9876543210", "Red lehenga", 0, "Fine."))).isNotEmpty();
        assertThat(validator.validate(
                new SubmitReviewRequest("Priya S", "9876543210", "Red lehenga", 6, "Fine."))).isNotEmpty();
    }

    @Test
    void should_reject_a_phone_that_is_not_a_ten_digit_indian_mobile_number() {
        assertThat(validator.validate(
                new SubmitReviewRequest("Priya S", "1234567890", "Red lehenga", 5, "Fine."))).isNotEmpty();
        assertThat(validator.validate(
                new SubmitReviewRequest("Priya S", "98765", "Red lehenga", 5, "Fine."))).isNotEmpty();
    }

    @Test
    void should_reject_a_blank_reviewer_name() {
        assertThat(validator.validate(
                new SubmitReviewRequest("   ", "9876543210", "Red lehenga", 5, "Fine."))).isNotEmpty();
    }
}
