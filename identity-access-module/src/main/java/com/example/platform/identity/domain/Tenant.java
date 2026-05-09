package com.example.platform.identity.domain;

import java.time.Instant;

public record Tenant(
        String id,
        String name,
        TenantStatus status,
        Instant createdAt) {

    public enum TenantStatus {
        ACTIVE, SUSPENDED
    }
}
