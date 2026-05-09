package com.example.platform.identity.api.dto;

import com.example.platform.identity.domain.User;
import jakarta.validation.constraints.NotBlank;

public record CreateUserRequest(
        @NotBlank String username,
        @NotBlank String email,
        String role) {

    public User.UserRole roleOrDefault() {
        if (role == null || role.isBlank()) {
            return User.UserRole.MEMBER;
        }
        return User.UserRole.valueOf(role.toUpperCase());
    }
}
