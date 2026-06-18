package com.example.platform.shared.capability;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Context for invoking system actions or extension providers.
 *
 * <p>InvocationContext carries tenant, user, and request information
 * without exposing raw secrets.</p>
 *
 * <p><strong>Contract only:</strong> This defines the invocation context shape.
 * No runtime context propagation is implemented.</p>
 */
public record InvocationContext(
    String tenantId,
    String userId,
    String projectId,
    String requestId,
    String idempotencyKey,
    Instant deadline,
    Map<String, Object> featureFlags,
    List<CredentialRef> credentialRefs,
    AuditContext auditContext
) {
    public record AuditContext(
        String traceId,
        String spanId,
        String sourceService,
        Instant timestamp
    ) {}

    /**
     * Create a context for testing or simple cases.
     */
    public static InvocationContext simple(String tenantId, String userId) {
        return new InvocationContext(
            tenantId,
            userId,
            null,
            java.util.UUID.randomUUID().toString(),
            null,
            null,
            Map.of(),
            List.of(),
            new AuditContext(null, null, null, Instant.now())
        );
    }
}
