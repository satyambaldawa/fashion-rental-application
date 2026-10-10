package com.fashionrental.receipt.model.request;

import com.fashionrental.receipt.Receipt;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CancelReceiptRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void should_reject_null_reason() {
        CancelReceiptRequest request = new CancelReceiptRequest(null, null);

        Set<ConstraintViolation<CancelReceiptRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("Cancellation reason is required");
    }

    @Test
    void should_reject_other_with_null_detail() {
        CancelReceiptRequest request = new CancelReceiptRequest(Receipt.CancellationReason.OTHER, null);

        Set<ConstraintViolation<CancelReceiptRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("Please describe the reason when choosing Other");
    }

    @Test
    void should_reject_other_with_empty_detail() {
        CancelReceiptRequest request = new CancelReceiptRequest(Receipt.CancellationReason.OTHER, "");

        Set<ConstraintViolation<CancelReceiptRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("Please describe the reason when choosing Other");
    }

    @Test
    void should_reject_other_with_whitespace_only_detail() {
        CancelReceiptRequest request = new CancelReceiptRequest(Receipt.CancellationReason.OTHER, "   ");

        Set<ConstraintViolation<CancelReceiptRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("Please describe the reason when choosing Other");
    }

    @Test
    void should_accept_other_with_detail_text() {
        CancelReceiptRequest request = new CancelReceiptRequest(Receipt.CancellationReason.OTHER, "Customer changed mind");

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void should_accept_wrong_order_without_detail() {
        CancelReceiptRequest request = new CancelReceiptRequest(Receipt.CancellationReason.WRONG_ORDER, null);

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void should_reject_detail_over_500_characters() {
        CancelReceiptRequest request = new CancelReceiptRequest(
                Receipt.CancellationReason.OTHER, "X".repeat(501));

        Set<ConstraintViolation<CancelReceiptRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .contains("reasonDetail");
    }

    @Test
    void should_accept_detail_at_500_characters() {
        CancelReceiptRequest request = new CancelReceiptRequest(
                Receipt.CancellationReason.OTHER, "X".repeat(500));

        assertThat(validator.validate(request)).isEmpty();
    }
}
