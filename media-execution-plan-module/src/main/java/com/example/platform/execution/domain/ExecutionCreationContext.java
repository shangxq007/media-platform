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
 * the plan digest or cache key.
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
     * Returns the parent plan ID as Optional.
     */
    public Optional<String> getParentPlanId() {
        return Optional.ofNullable(parentPlanId);
    }

    /**
     * Returns the trace ID as Optional.
     */
    public Optional<String> getTraceId() {
        return Optional.ofNullable(traceId);
    }

    /**
     * Returns the comment as Optional.
     */
    public Optional<String> getComment() {
        return Optional.ofNullable(comment);
    }

    /**
     * Creates a new context with an updated trace ID.
     */
    public ExecutionCreationContext withTraceId(String traceId) {
        return new ExecutionCreationContext(
                requestedByUserId, requestedByTenantId, requestPurpose,
                createdAt, traceId, parentPlanId, comment);
    }

    /**
     * Creates a new context with an updated comment.
     */
    public ExecutionCreationContext withComment(String comment) {
        return new ExecutionCreationContext(
                requestedByUserId, requestedByTenantId, requestPurpose,
                createdAt, traceId, parentPlanId, comment);
    }

    @Override
    public String toString() {
        return "creationCtx{" +
                "user=" + (requestedByUserId != null ? requestedByUserId : "") +
                ",tenant=" + (requestedByTenantId != null ? requestedByTenantId : "") +
                ",purpose=" + (requestPurpose != null ? requestPurpose : "") +
                ",at=" + createdAt +
                ",trace=" + (traceId != null ? traceId : "") +
                ",parent=" + (parentPlanId != null ? parentPlanId : "") +
                ",comment=" + (comment != null ? comment : "") +
                '}';
    }
}
