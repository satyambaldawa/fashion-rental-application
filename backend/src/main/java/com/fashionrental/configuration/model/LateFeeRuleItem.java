package com.fashionrental.configuration.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record LateFeeRuleItem(
    UUID id,
    @NotNull @Min(0) Integer durationFromHours,
    Integer durationToHours,
    @NotNull @DecimalMin("0.1") BigDecimal penaltyMultiplier,
    @NotNull Integer sortOrder,
    boolean isActive
) {}
