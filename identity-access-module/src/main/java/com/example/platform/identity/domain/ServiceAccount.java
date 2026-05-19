package com.example.platform.identity.domain;

import java.time.Instant;

public record ServiceAccount(
        String id,
        String tenantId,
        String workspaceId,
        String name,
        String description,
        ServiceAccountStatus status,
        Instant createdAt) {

    public enum ServiceAccountStatus {
        ACTIVE, INACTIVE, REVOKED
    }
}
