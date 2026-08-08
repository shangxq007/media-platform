package com.example.platform.billing.usage;

import java.util.Objects;

/**
 * Unified operation correlation reference.
 *
 * <p>Reuses existing operation/request/job/attempt/execution identifiers — no new ID
 * namespace. An operation ({@code operationId}) is distinct from an attempt
 * ({@code attemptId}): one logical operation may have multiple attempts, and an attempt
 * identity must not be overwritten by a retry.</p>
 *
 * @param operationId the operation identifier (required)
 * @param attemptId   the attempt identifier (nullable)
 */
public record OperationRef(String operationId, String attemptId) {

    public OperationRef {
        Objects.requireNonNull(operationId, "operationId must not be null");
        if (operationId.isBlank()) {
            throw new IllegalArgumentException("operationId must not be blank");
        }
    }

    /** Operation only (no attempt). */
    public static OperationRef of(String operationId) {
        return new OperationRef(operationId, null);
    }

    /** Operation + attempt. */
    public static OperationRef of(String operationId, String attemptId) {
        return new OperationRef(operationId, attemptId);
    }
}
