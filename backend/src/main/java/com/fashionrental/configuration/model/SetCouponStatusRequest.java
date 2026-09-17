package com.fashionrental.configuration.model;

import jakarta.validation.constraints.NotNull;

public record SetCouponStatusRequest(@NotNull Boolean isActive) {}
