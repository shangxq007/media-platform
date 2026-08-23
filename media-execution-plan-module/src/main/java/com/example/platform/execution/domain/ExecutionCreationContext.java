package com.example.platform.execution.domain;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Context for the creation of an execution plan.
 *
 * <p>Immutable value object capturing the origin and intent of an execution
 * plan — who requested it, when, and why. This metadata does NOT affect
 * the plan digest or cache key. FROZEN Decision Recovery canonical surface
 * (REUSE_AS_CANONICAL): parentPlanId is String; createdAt REQUIRED.
 */
public record ExecutionCreationContext(
        String requestedByUserId,
        String requestedByTenantId,
        String requestPurpose,
        Instant createdAt,
        String traceId,
        String parentPlanId,
        String comment
) implements Serializable {

    public ExecutionCreationContext {
        Objects.requireNonNull(createdAt, "createdAt");
        // All other fields may be null
    }

    /**
     * Creates a minimal creation context with only the timestamp.
     */
    public static ExecutionCreationContext minimal(Instant createdAt) {
        return new ExecutionCreationContext(null, null, null, createdAt, null, null, null);
    }

    /**
     * Creates a creation context with user and tenant.
     */
    public static ExecutionCreationContext forUser(String userId, String tenantId, Instant createdAt) {
        return new ExecutionCreationContext(userId, tenantId, null, createdAt, null, null, null);
    }

    /**
     * Returns the user ID as Optional.
     */
    public Optional<String> getRequestedByUserId() {
        return Optional.ofNullable(requestedByUserId);
    }

    /**
     * Returns the tenant ID as Optional.
     */
    public Optional<String> getRequestedByTenantId() {
        return Optional.ofNullable(requestedByTenantId);
    }

    /**
     * Returns the request purpose as Optional.
     */
    public Optional<String> getRequestPurpose() {
        return Optional.ofNullable(requestPurpose);
    }

    /**
     * Returns the trace ID as Optional.
     */
    public Optional<String> getTraceId() {
        return Optional.ofNullable(traceId);
    }

    /**
     * Returns the parent plan ID as Optional.
     */
    public Optional<String> getParentPlanId() {
        return Optional.ofNullable(parentPlanId);
    }

    /**
     * Returns the comment as Optional.
     */
    public Optional<String> getComment() {
        return Optional.ofNullable(comment);
    }
}
