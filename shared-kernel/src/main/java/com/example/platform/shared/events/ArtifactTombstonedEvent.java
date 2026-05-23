package com.example.platform.shared.events;

import java.time.Instant;

/** Published when a catalog artifact is marked {@code TOMBSTONED}. */
public record ArtifactTombstonedEvent(
        String artifactId,
        String projectId,
        String storageUri,
        Instant tombstonedAt) {}
