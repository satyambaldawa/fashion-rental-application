package com.fashionrental.configuration.model;

import java.math.BigDecimal;
import java.util.UUID;

public record LateFeeRuleResponse(
    UUID id,
    int durationFromHours,
    Integer durationToHours,
    BigDecimal penaltyMultiplier,
    int sortOrder,
    boolean isActive,
    String label
) {}
