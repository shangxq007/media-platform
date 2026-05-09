package com.example.platform.shared.events;

import java.time.Instant;

public record RenderJobStatusChangedEvent(
        String renderJobId,
        String projectId,
        String oldStatus,
        String newStatus,
        Instant updatedAt) {}
