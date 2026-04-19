package com.fashionrental.configuration.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record UpdateLateFeeRulesRequest(
    @NotEmpty @Valid List<LateFeeRuleItem> rules
) {}
