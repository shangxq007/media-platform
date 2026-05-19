package com.example.platform.entitlement.domain;

import java.time.Instant;

public record EntitlementOverride(
        String id,
        String subjectType,
        String subjectId,
        String overrideKind,
        String overridePayload,
        Instant effectiveAt,
        Instant expiresAt,
        String status,
        Instant createdAt,
        Instant updatedAt
) {}
