package com.example.platform.render.domain.renderplan;

import java.util.Objects;

/**
 * Logical RenderPlan instance identity (correlation/traceability).
 *
 * <p>Derived as {@code "rp:" + revisionId + ":" + requestId}. This is a logical
 * instance handle only — it is NOT part of the plan fingerprint (C7) and is never
 * persisted as canonical authority.
 */
public record RenderPlanId(String value) {

    public RenderPlanId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("RenderPlanId must not be blank");
        }
    }

    /**
     * Factory: derives a stable plan id from the revision and request ids.
     */
    public static RenderPlanId of(String revisionId, String requestId) {
        Objects.requireNonNull(revisionId, "revisionId");
        Objects.requireNonNull(requestId, "requestId");
        if (revisionId.isBlank()) {
            throw new IllegalArgumentException("revisionId must not be blank");
        }
        if (requestId.isBlank()) {
            throw new IllegalArgumentException("requestId must not be blank");
        }
        return new RenderPlanId("rp:" + revisionId + ":" + requestId);
    }

    @Override
    public String toString() {
        return value;
    }
}
