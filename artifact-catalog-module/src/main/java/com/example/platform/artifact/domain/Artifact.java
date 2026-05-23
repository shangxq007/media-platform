package com.example.platform.artifact.domain;

import java.time.Instant;

/**
 * Domain record representing a persisted artifact.
 *
 * <p>Fields align with the {@code artifact} table created in Flyway migration V7.</p>
 *
 * @param id          unique artifact identifier (prefixed {@code art_})
 * @param renderJobId the render job that produced this artifact
 * @param projectId   the project this artifact belongs to
 * @param storageUri  URI where the artifact content is stored
 * @param format      media format (e.g., {@code mp4}, {@code mov})
 * @param resolution  resolution string (e.g., {@code 1920x1080})
 * @param duration     media duration in seconds
 * @param status       lifecycle status (defaults to ACTIVE when absent in legacy rows)
 * @param tombstonedAt when status became TOMBSTONED
 * @param createdAt    timestamp when the artifact was registered
 */
public record Artifact(
        String id,
        String renderJobId,
        String projectId,
        String storageUri,
        String format,
        String resolution,
        Long duration,
        ArtifactStatus status,
        Instant tombstonedAt,
        Instant createdAt) {

    public Artifact {
        if (status == null) {
            status = ArtifactStatus.ACTIVE;
        }
    }

    public boolean isUsable() {
        return status == ArtifactStatus.ACTIVE;
    }
}
