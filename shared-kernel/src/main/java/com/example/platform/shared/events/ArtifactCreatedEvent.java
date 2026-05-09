package com.example.platform.shared.events;

import java.time.Instant;

public record ArtifactCreatedEvent(
        String artifactId,
        String renderJobId,
        String projectId,
        String storageUri,
        Instant createdAt) {}
