package com.fashionrental.config.model;

import com.fashionrental.config.AppUser;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String username,
        AppUser.Role role,
        OffsetDateTime createdAt
) {}
