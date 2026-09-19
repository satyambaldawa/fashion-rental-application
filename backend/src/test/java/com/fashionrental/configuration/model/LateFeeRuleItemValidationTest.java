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
        return new LateFeeRuleItem(null, 0, 3, multiplier, 1, true);
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
}
