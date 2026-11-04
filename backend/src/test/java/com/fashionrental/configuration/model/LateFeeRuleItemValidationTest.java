package com.fashionrental.configuration.model;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LateFeeRuleItemValidationTest {

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

    private static LateFeeRuleItem ruleWithMultiplier(BigDecimal multiplier) {
        return new LateFeeRuleItem(null, 0, 3, multiplier, 1, true, null);
    }

    @Test
    void should_accept_a_zero_penalty_multiplier() {
        Set<ConstraintViolation<LateFeeRuleItem>> violations =
                validator.validate(ruleWithMultiplier(BigDecimal.ZERO));

        assertThat(violations).isEmpty();
    }

    @Test
    void should_reject_a_negative_penalty_multiplier() {
        Set<ConstraintViolation<LateFeeRuleItem>> violations =
                validator.validate(ruleWithMultiplier(new BigDecimal("-0.1")));

        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("penaltyMultiplier");
    }

    @Test
    void should_accept_a_new_rule_without_a_version() {
        LateFeeRuleItem newRule = new LateFeeRuleItem(null, 0, 3, BigDecimal.ONE, 1, true, null);

        assertThat(validator.validate(newRule)).isEmpty();
    }

    @Test
    void should_reject_an_existing_rule_without_a_version() {
        LateFeeRuleItem existingWithoutVersion =
                new LateFeeRuleItem(UUID.randomUUID(), 0, 3, BigDecimal.ONE, 1, true, null);

        Set<ConstraintViolation<LateFeeRuleItem>> violations = validator.validate(existingWithoutVersion);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("version is required for an existing late fee rule");
    }

    @Test
    void should_accept_an_existing_rule_with_a_version() {
        LateFeeRuleItem existing = new LateFeeRuleItem(UUID.randomUUID(), 0, 3, BigDecimal.ONE, 1, true, 2L);

        assertThat(validator.validate(existing)).isEmpty();
    }
}
