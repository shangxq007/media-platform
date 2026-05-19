package com.example.platform.identity.domain;

import java.time.Instant;

public record ApiClient(
        String id,
        String tenantId,
        String workspaceId,
        String name,
        String clientKeyHash,
        ApiClientStatus status,
        Instant createdAt) {

    public enum ApiClientStatus {
        ACTIVE, INACTIVE, REVOKED
    }
}
