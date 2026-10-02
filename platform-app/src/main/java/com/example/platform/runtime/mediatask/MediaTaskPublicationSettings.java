package com.example.platform.runtime.mediatask;

import com.example.platform.artifact.domain.ArtifactKind;
import com.example.platform.artifact.domain.ArtifactMediaType;
import com.example.platform.storage.contract.StorageProviderId;
import com.example.platform.workerfabric.reuse.TaskOutputPublicationContext;
import java.time.Clock;
import java.util.Objects;

/**
 * P2-5b-2b-2a: the worker's configured publication settings for media task outputs.
 *
 * <p>These values are deployment configuration, not task facts: the scope the worker publishes in
 * ({@code projectId}), the storage it writes through ({@code storageProvider}, {@code region}) and the
 * format it produces ({@code mediaType}, {@code artifactKind}). They are held as configured and
 * validated only when a context is built, so a worker whose publication scope is not configured still
 * boots and then fails closed the moment it is asked to execute — never with an invented value.
 */
public record MediaTaskPublicationSettings(
        String projectId,
        String storageProvider,
        String region,
        ArtifactMediaType mediaType,
        ArtifactKind artifactKind,
        Clock clock) {

    public MediaTaskPublicationSettings {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(storageProvider, "storageProvider");
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(mediaType, "mediaType");
        Objects.requireNonNull(artifactKind, "artifactKind");
        Objects.requireNonNull(clock, "clock");
    }

    /**
     * Builds the publication context for one task output.
     *
     * @throws IllegalArgumentException when the configured publication scope is blank (fail closed)
     */
    public TaskOutputPublicationContext contextFor(String tenantId, String renderJobId) {
        return new TaskOutputPublicationContext(
                tenantId,
                renderJobId,
                projectId,
                new StorageProviderId(storageProvider),
                region,
                mediaType,
                artifactKind,
                clock.instant());
    }
}
