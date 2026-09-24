package com.example.platform.composition.app;

import java.util.Map;
import java.util.Objects;

/** Immutable, server-scoped request handed to the provider/runtime boundary. */
public record CompositionExecutionRequest(
        String tenantId,
        String workspaceId,
        String sourceArtifactId,
        String sourceArtifactRevision,
        String workflowId,
        long workflowRevision,
        String capabilityId,
        String capabilityVersion,
        String idempotencyKey,
        Map<String, Object> parameters,
        Map<String, String> entitlementSnapshot) {
    public CompositionExecutionRequest {
        require(tenantId, "tenantId"); require(workspaceId, "workspaceId");
        require(sourceArtifactId, "sourceArtifactId"); require(sourceArtifactRevision, "sourceArtifactRevision");
        require(workflowId, "workflowId"); require(capabilityId, "capabilityId");
        require(capabilityVersion, "capabilityVersion"); require(idempotencyKey, "idempotencyKey");
        if (workflowRevision < 0) throw new IllegalArgumentException("workflowRevision must be non-negative");
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        entitlementSnapshot = entitlementSnapshot == null ? Map.of() : Map.copyOf(entitlementSnapshot);
    }
    private static void require(String value, String name) { if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required"); }
}
