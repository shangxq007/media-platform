package com.example.platform.shared.events;

public record RenderJobCreatedEvent(
        String renderJobId,
        String projectId,
        String timelineSnapshotId,
        String profile,
        String primaryBackend) {}
