package com.example.platform.execution.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * ROADMAP #21 provenance-only creation context (frozen ledger
 * REUSE_AS_CANONICAL).
 *
 * <p>FROZEN canonical shape (Decision Recovery): provenance-only metadata.
 * ALL fields EXCLUDED from semantic digests (identity/provenance, never
 * semantic content). Lightly wired is acceptable; do not shrink the frozen
 * shape because #21 does not consume every field yet.
 */
public record ExecutionCreationContext(
        String requestedByUserId,
        String requestedByTenantId,
        String requestPurpose,
        Instant createdAt,
        String traceId,
        com.example.platform.execution.domain.ExecutionPlanId parentPlanId,
        String comment) {

    public ExecutionCreationContext {
        // all fields optional provenance metadata — never semantic
    }

    public static ExecutionCreationContext absent() {
        return new ExecutionCreationContext(null, null, null, null, null, null, null);
    }
}
