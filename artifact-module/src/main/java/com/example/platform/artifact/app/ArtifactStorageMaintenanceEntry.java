package com.example.platform.artifact.app;

import com.example.platform.artifact.domain.ArtifactState;

/**
 * Internal application projection used only for Artifact storage maintenance.
 *
 * <p>{@code storageObjectId} is a physical storage coordinate for internal maintenance only. It is
 * never Artifact product identity and must never be exposed through a product or public API projection.
 */
public record ArtifactStorageMaintenanceEntry(
        String artifactId,
        String projectId,
        ArtifactState state,
        String storageObjectId) {}
