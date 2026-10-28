package com.example.platform.workerfabric.reuse;

import com.example.platform.artifact.domain.ArtifactKind;
import com.example.platform.artifact.domain.ArtifactMediaType;
import com.example.platform.storage.contract.StorageProviderId;
import java.time.Instant;
import java.util.Objects;

/**
 * Everything the caller owns when planning a task-output publication: the scope it runs in, the
 * storage it is configured to write to, the format the provider produces, and the clock reading.
 *
 * <p>None of these are derivable from the executable task, so the planner takes them rather than
 * inventing them. {@code storageProviderId} and {@code region} mirror the configured storage backend
 * (see {@code StorageOutputService}, which selects the provider and region the same way);
 * {@code artifactKind}/{@code mediaType} describe the bytes the provider is about to produce.
 *
 * @param tenantId tenant scope of the render job (from the bound graph reference)
 * @param renderJobId render job identity (from the bound graph reference)
 * @param projectId project scope that owns the storage namespace
 * @param storageProviderId configured storage provider the output is written through
 * @param region storage region ("local" for the local backend, mirroring {@code StorageOutputService})
 * @param mediaType media type of the produced output
 * @param artifactKind artifact kind of the produced output
 * @param evaluatedAt caller's clock reading for the commit metadata timestamps
 */
public record TaskOutputPublicationContext(
        String tenantId,
        String renderJobId,
        String projectId,
        StorageProviderId storageProviderId,
        String region,
        ArtifactMediaType mediaType,
        ArtifactKind artifactKind,
        Instant evaluatedAt,
        String executionId) {

    public TaskOutputPublicationContext(String tenantId, String renderJobId, String projectId,
            StorageProviderId storageProviderId, String region, ArtifactMediaType mediaType,
            ArtifactKind artifactKind, Instant evaluatedAt) {
        this(tenantId,renderJobId,projectId,storageProviderId,region,mediaType,artifactKind,evaluatedAt,null);
    }
    public static TaskOutputPublicationContext forExecution(String tenantId,String projectId,String executionId,
            StorageProviderId storageProviderId,String region,ArtifactMediaType mediaType,
            ArtifactKind artifactKind,Instant evaluatedAt) {
        return new TaskOutputPublicationContext(tenantId,null,projectId,storageProviderId,region,mediaType,artifactKind,evaluatedAt,executionId);
    }
    public String producingIdentity() { return renderJobId == null ? "execution:"+executionId : "render:"+renderJobId; }
    public TaskOutputPublicationContext {
        requireText(tenantId, "tenantId");
        if ((renderJobId == null) == (executionId == null)) {
            throw new IllegalArgumentException("one typed producing identity is required");
        }
        requireText(renderJobId == null ? executionId : renderJobId, "producing identity");
        requireText(projectId, "projectId");
        Objects.requireNonNull(storageProviderId, "storageProviderId");
        requireText(region, "region");
        Objects.requireNonNull(mediaType, "mediaType");
        Objects.requireNonNull(artifactKind, "artifactKind");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt");
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
