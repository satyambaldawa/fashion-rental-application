package com.fashionrental.config.model;

import com.fashionrental.config.AppUser;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank String username,
        @NotBlank @Size(min = 6) String password,
        @NotNull AppUser.Role role
) {}
