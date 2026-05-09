package com.example.platform.shared.events;

import java.time.Instant;

/**
 * Published when a render job completes successfully.
 * Consumed by audit-compliance-module (audit trail) and notification-module.
 */
public record RenderJobCompletedEvent(
        String renderJobId,
        String projectId,
        String artifactId,
        String storageUri,
        Instant completedAt) {}
