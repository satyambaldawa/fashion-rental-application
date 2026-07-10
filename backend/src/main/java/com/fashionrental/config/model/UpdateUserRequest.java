package com.fashionrental.config.model;

import com.fashionrental.config.AppUser;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        AppUser.Role role,
        @Size(min = 6, message = "Password must be at least 6 characters") String password
) {}
