package com.example.platform.execution.composition;

import java.util.Objects;

/**
 * Typed contract decision for the Composition to provider-bound planning seam.
 *
 * <p>This decision is deliberately fail-closed. Composition may only proceed
 * once a canonical authority has supplied an immutable published revision,
 * physical plan units, provider candidate, and compatibility proofs. It must
 * not manufacture those facts from a provider-neutral Composition document.
 */
public record CompositionProviderBoundPlanningDecision(Status status, Blocker blocker,
        String detail) {

    /** No READY value exists until the canonical projection authority exists. */
    public enum Status { BLOCKED }

    public enum Blocker {
        PUBLISHED_IMMUTABLE_REVISION_UNAVAILABLE,
        PROVIDER_BOUND_GRAPH_AUTHORITY_UNAVAILABLE,
        PHYSICAL_PLAN_PROJECTION_UNAVAILABLE,
        RUNTIME_AUTHORITY_UNAVAILABLE
    }

    public CompositionProviderBoundPlanningDecision {
        Objects.requireNonNull(status, "status");
        if (blocker == null) {
            throw new IllegalArgumentException("blocked decision requires a blocker");
        }
        if (detail == null || detail.isBlank()) {
            throw new IllegalArgumentException("decision detail is required");
        }
    }

    public static CompositionProviderBoundPlanningDecision blocked(Blocker blocker,
            String detail) {
        return new CompositionProviderBoundPlanningDecision(Status.BLOCKED,
                Objects.requireNonNull(blocker, "blocker"), detail);
    }

    public boolean ready() {
        return false;
    }
}
