package com.example.platform.identity.domain;

import java.time.Instant;

public record Project(
        String id,
        String tenantId,
        String name,
        String description,
        ProjectStatus status,
        Instant createdAt) {

    public enum ProjectStatus {
        ACTIVE, ARCHIVED
    }
}
