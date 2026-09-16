package com.fashionrental.receipt.model.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CheckoutPreviewRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    private static final OffsetDateTime START = OffsetDateTime.parse("2026-04-21T10:00:00+05:30");
    private static final OffsetDateTime END = OffsetDateTime.parse("2026-04-24T10:00:00+05:30");

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
    void should_accept_preview_with_only_ad_hoc_items() {
        CheckoutPreviewRequest request = new CheckoutPreviewRequest(
                START, END, List.of(), List.of(new AdHocLineItem("Red Sherwani", "L", 500, 1000, 1)));

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void should_reject_preview_when_both_item_lists_are_empty() {
        CheckoutPreviewRequest request = new CheckoutPreviewRequest(START, END, List.of(), List.of());

        Set<ConstraintViolation<CheckoutPreviewRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("A preview needs at least one item");
    }

    @Test
    void should_treat_missing_ad_hoc_list_as_empty() {
        CheckoutPreviewRequest request = new CheckoutPreviewRequest(START, END, List.of(), null);

        Set<ConstraintViolation<CheckoutPreviewRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("A preview needs at least one item");
    }

    @Test
    void should_treat_missing_items_list_as_empty() {
        CheckoutPreviewRequest request = new CheckoutPreviewRequest(
                START, END, null, List.of(new AdHocLineItem("Red Sherwani", "L", 500, 1000, 1)));

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void should_reject_null_ad_hoc_element() {
        CheckoutPreviewRequest request = new CheckoutPreviewRequest(
                START, END, List.of(new CheckoutLineItem(UUID.randomUUID(), 1)), Arrays.asList((AdHocLineItem) null));

        Set<ConstraintViolation<CheckoutPreviewRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("adHocItems[0].<list element>");
    }
}
