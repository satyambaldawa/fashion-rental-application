package com.fashionrental.configuration.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.OffsetDateTime;

public record CreateCouponRequest(
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{3,32}$",
                message = "Code must be 3-32 letters, digits, hyphens or underscores") String code,
        @NotBlank String discountType,
        @NotNull @Min(1) Integer value,
        @Min(1) Integer minSubtotal,
        @NotNull OffsetDateTime validFrom,
        @NotNull OffsetDateTime validTo,
        @Min(1) Integer usageLimit
) {}
