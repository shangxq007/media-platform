package com.example.platform.composition.app;

import com.example.platform.workerfabric.domain.providernative.ProviderExecutionOutput;
import java.util.Optional;

/** Storage and Artifact owners' typed materialization boundary. */
public interface CompositionMaterializationPort {
    /** Idempotency lookup; a completed attempt must be returned without re-dispatch. */
    default Optional<CompositionMaterializationAdapter.Result> findCommitted(CompositionExecutionRequest request) {
        return Optional.empty();
    }

    IssuedOutput issue(CompositionExecutionRequest request, ProviderExecutionOutput output);
    CommittedArtifact commitArtifact(CompositionExecutionRequest request, IssuedOutput output);
    void compensate(IssuedOutput output);
    default void recordCommitted(CompositionExecutionRequest request, IssuedOutput issued,
            CommittedArtifact artifact) {}

    record IssuedOutput(String tenantId, String workspaceId, String placementId, String digest, long length) {
        public IssuedOutput {
            require(tenantId, "tenantId"); require(workspaceId, "workspaceId"); require(placementId, "placementId"); require(digest, "digest");
            if (length < 0) throw new IllegalArgumentException("length must be non-negative");
        }
    }
    record CommittedArtifact(String tenantId, String workspaceId, String artifactId, String digest) {
        public CommittedArtifact { require(tenantId, "tenantId"); require(workspaceId, "workspaceId"); require(artifactId, "artifactId"); require(digest, "digest"); }
    }
    private static void require(String value, String name) { if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required"); }
}
