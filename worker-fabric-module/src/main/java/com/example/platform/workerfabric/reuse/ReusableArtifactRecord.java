package com.example.platform.workerfabric.reuse;

import com.example.platform.artifact.app.ArtifactPinService.ArtifactPin;
import com.example.platform.execution.taskgraph.ExecutableTaskId;
import com.example.platform.execution.taskgraph.ExecutionReuseKey;
import com.example.platform.workerfabric.domain.ExecutionAttemptId;
import com.example.platform.workerfabric.domain.ExecutionOwnershipGeneration;
import java.time.Instant;
import java.util.Objects;

/** Tenant-scoped reuse-index metadata. It carries an Artifact pin, never a storage location. */
public record ReusableArtifactRecord(
        String tenantId,
        ExecutionReuseKey executionReuseKey,
        ArtifactPin artifactPin,
        ExecutableTaskId executableTaskId,
        ExecutionAttemptId executionAttemptId,
        ExecutionOwnershipGeneration ownershipGeneration,
        Instant publishedAt) {

    public ReusableArtifactRecord {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(executionReuseKey, "executionReuseKey");
        Objects.requireNonNull(artifactPin, "artifactPin");
        Objects.requireNonNull(artifactPin.artifactId(), "artifactPin.artifactId");
        Objects.requireNonNull(artifactPin.contentDigest(), "artifactPin.contentDigest");
        Objects.requireNonNull(executableTaskId, "executableTaskId");
        Objects.requireNonNull(executionAttemptId, "executionAttemptId");
        Objects.requireNonNull(ownershipGeneration, "ownershipGeneration");
        Objects.requireNonNull(publishedAt, "publishedAt");
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
    }
}
