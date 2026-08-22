package com.example.platform.execution.domain;

import java.util.Objects;

/**
 * ROADMAP #21 provenance-only creation metadata (frozen ledger
 * REUSE_AS_CANONICAL). correlation/createdAt/trace identity is
 * PROVENANCE_ONLY and EXCLUDED from semantic digests.
 */
public record ExecutionCreationContext(String correlationId, String createdAt) {

    public static ExecutionCreationContext absent() {
        return new ExecutionCreationContext(null, null);
    }

    public ExecutionCreationContext {
        // both nullable — provenance-only, never semantic
    }
}
