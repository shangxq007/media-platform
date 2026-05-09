package com.example.platform.identity.domain;

import java.time.Instant;

public record User(
        String id,
        String tenantId,
        String username,
        String email,
        UserRole role,
        UserStatus status,
        Instant createdAt) {

    public enum UserRole {
        ADMIN, MEMBER, VIEWER
    }

    public enum UserStatus {
        ACTIVE, INACTIVE
    }
}
