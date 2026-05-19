package com.example.platform.identity.domain;

import java.time.Instant;

public record Workspace(
        String id,
        String tenantId,
        String name,
        String description,
        String planTier,
        WorkspaceStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public enum WorkspaceStatus {
        ACTIVE, SUSPENDED, ARCHIVED
    }
}
