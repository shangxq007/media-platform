package com.example.platform.storage.api;

import com.example.platform.storage.domain.StorageObjectRef;
import java.util.List;

/**
 * Port interface for storage catalog operations.
 * Exposed as part of the storage-module API surface for other modules to consume.
 */
public interface StorageCatalogPort {

    /**
     * Register a completed artifact in the storage catalog.
     *
     * @param renderJobId the render job that produced the artifact
     * @param projectId   the project the artifact belongs to
     * @param providerRef the storage reference from the blob storage provider
     * @return the registered artifact reference
     */
    ArtifactRef registerArtifact(String renderJobId, String projectId, StorageObjectRef providerRef);

    /**
     * Find all artifacts associated with a render job.
     *
     * @param renderJobId the render job ID
     * @return list of artifact references
     */
    List<ArtifactRef> findArtifactsByJob(String renderJobId);

    /**
     * Value object representing a registered artifact reference.
     */
    record ArtifactRef(
            String artifactId,
            String renderJobId,
            String projectId,
            String storageUri,
            String format,
            String resolution,
            long duration,
            java.time.Instant createdAt) {}
}
