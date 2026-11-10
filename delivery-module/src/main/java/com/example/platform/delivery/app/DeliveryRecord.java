package com.example.platform.delivery.app;

import java.time.OffsetDateTime;

/** Immutable accepted delivery reference. READY does not bypass live authorization/Artifact checks. */
public record DeliveryRecord(String id, String tenantId, String projectId, String sourceRenderJobId,
        String artifactId, String idempotencyKey, String requestHash, String requesterId, OffsetDateTime createdAt) {}
