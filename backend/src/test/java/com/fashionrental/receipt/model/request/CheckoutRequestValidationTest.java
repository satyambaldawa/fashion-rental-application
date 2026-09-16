package com.fashionrental.receipt.model.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CheckoutRequestValidationTest {

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

    private static CheckoutRequest withOnlyAdHoc(AdHocLineItem line) {
        return new CheckoutRequest(UUID.randomUUID(), START, END, List.of(), List.of(line), null);
    }

    @Test
    void should_accept_request_with_only_ad_hoc_items() {
        Set<ConstraintViolation<CheckoutRequest>> violations =
                validator.validate(withOnlyAdHoc(new AdHocLineItem("Red Sherwani", "L", 500, 1000, 1)));

        assertThat(violations).isEmpty();
    }

    @Test
    void should_reject_request_when_both_item_lists_are_empty() {
        CheckoutRequest request = new CheckoutRequest(UUID.randomUUID(), START, END, List.of(), List.of(), null);

        Set<ConstraintViolation<CheckoutRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("A receipt needs at least one item");
    }

    @Test
    void should_reject_ad_hoc_item_with_blank_name() {
        Set<ConstraintViolation<CheckoutRequest>> violations =
                validator.validate(withOnlyAdHoc(new AdHocLineItem("   ", "L", 500, 1000, 1)));

        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("adHocItems[0].name");
    }

    @Test
    void should_reject_ad_hoc_item_priced_below_one_rupee() {
        Set<ConstraintViolation<CheckoutRequest>> violations =
                validator.validate(withOnlyAdHoc(new AdHocLineItem("Red Sherwani", "L", 0, 1000, 1)));

        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("adHocItems[0].flatPrice");
    }

    @Test
    void should_accept_ad_hoc_item_with_zero_deposit() {
        Set<ConstraintViolation<CheckoutRequest>> violations =
                validator.validate(withOnlyAdHoc(new AdHocLineItem("Red Sherwani", "L", 500, 0, 1)));

        assertThat(violations).isEmpty();
    }

    @Test
    void should_treat_missing_ad_hoc_list_as_empty() {
        CheckoutRequest request = new CheckoutRequest(UUID.randomUUID(), START, END, List.of(), null, null);

        Set<ConstraintViolation<CheckoutRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("A receipt needs at least one item");
    }

    @Test
    void should_treat_missing_items_list_as_empty() {
        CheckoutRequest request = new CheckoutRequest(
                UUID.randomUUID(), START, END,
                null, List.of(new AdHocLineItem("Red Sherwani", "L", 500, 1000, 1)), null);

        assertThat(validator.validate(request)).isEmpty();
    }
}
