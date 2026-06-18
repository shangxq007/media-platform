package com.example.platform.shared.capability.execution;

import com.example.platform.shared.capability.ArtifactRef;
import com.example.platform.shared.capability.CredentialRef;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Context for system action execution.
 *
 * <p><strong>Contract only:</strong> This defines the execution context shape.
 * Runtime execution is not implemented.</p>
 */
public record SystemActionExecutionContext(
    String tenantId,
    String userId,
    String projectId,
    String requestId,
    String idempotencyKey,
    Instant deadline,
    boolean dryRun,
    Map<String, Object> auditContext,
    List<CredentialRef> credentialRefs,
    List<ArtifactRef> artifactRefs
) {
    public SystemActionExecutionContext {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId must not be blank");
        }
        auditContext = auditContext != null ? Map.copyOf(auditContext) : Map.of();
        credentialRefs = credentialRefs != null ? List.copyOf(credentialRefs) : List.of();
        artifactRefs = artifactRefs != null ? List.copyOf(artifactRefs) : List.of();
    }
}
