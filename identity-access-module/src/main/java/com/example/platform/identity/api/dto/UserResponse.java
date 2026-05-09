package com.example.platform.identity.api.dto;

import com.example.platform.identity.domain.User;

public record UserResponse(
        String id,
        String tenantId,
        String username,
        String email,
        String role,
        String status,
        java.time.Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.id(), user.tenantId(), user.username(),
                user.email(), user.role().name(), user.status().name(), user.createdAt());
    }
}
