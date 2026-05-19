package com.example.platform.extension.domain;

import java.time.OffsetDateTime;

public record RollbackPoint(
        String id,
        String extensionCode,
        String version,
        String artifactUri,
        String configSnapshot,
        String routingRuleIds,
        OffsetDateTime createdAt,
        String createdBy,
        boolean active
) {
    public RollbackPoint {
        if (extensionCode == null || extensionCode.isBlank()) {
            throw new IllegalArgumentException("extensionCode must not be blank");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("version must not be blank");
        }
    }
}
