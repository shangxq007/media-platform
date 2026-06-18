package com.example.platform.shared.capability.hook;

import com.example.platform.shared.capability.CredentialRef;
import com.example.platform.shared.capability.ArtifactRef;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Represents an invocation of a hook handler.
 *
 * <p><strong>Contract only:</strong> This defines the hook invocation shape.
 * Hook runtime is not implemented.</p>
 */
public record HookInvocation(
    String hookPointKey,
    String tenantId,
    String userId,
    String projectId,
    String requestId,
    String correlationId,
    String idempotencyKey,
    Instant deadline,
    Map<String, Object> input,
    List<ArtifactRef> artifactRefs,
    List<CredentialRef> credentialRefs
) {
    public HookInvocation {
        if (hookPointKey == null || hookPointKey.isBlank()) {
            throw new IllegalArgumentException("hookPointKey must not be blank");
        }
        input = input != null ? Map.copyOf(input) : Map.of();
        artifactRefs = artifactRefs != null ? List.copyOf(artifactRefs) : List.of();
        credentialRefs = credentialRefs != null ? List.copyOf(credentialRefs) : List.of();
    }
}
