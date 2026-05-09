package com.example.platform.shared.events;

import java.time.Instant;

/**
 * Published when a render job fails.
 * Consumed by audit-compliance-module (audit trail) and notification-module.
 */
public record RenderJobFailedEvent(
        String renderJobId,
        String projectId,
        String error,
        Instant failedAt) {}
