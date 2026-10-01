package com.example.platform.operation.operation;

import java.util.Objects;

/**
 * Exact immutable base pin (CAPABILITY_OPERATION_PARAMETER_MODEL / E-2b,
 * DOM-OPERATION-001 layer 3).
 *
 * <p>Mirrors the exact-base semantics of {@code OperationRequest}
 * ({@code baseRevisionId} required; {@code baseContentHash} optional). A
 * resolution must fail closed on a base mismatch — no mutable-latest fallback.</p>
 */
public record BasePin(String baseRevisionId, String baseContentHash) {

    public BasePin {
        Objects.requireNonNull(baseRevisionId, "baseRevisionId");
        if (baseRevisionId.isBlank()) {
            throw new IllegalArgumentException("baseRevisionId must not be blank");
        }
    }
}
