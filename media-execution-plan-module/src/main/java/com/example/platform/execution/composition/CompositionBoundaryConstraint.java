package com.example.platform.execution.composition;

import com.example.platform.execution.domain.ExecutionStepId;
import java.util.Objects;

/** Typed immutable evidence that a specific member boundary cannot be coalesced. */
public record CompositionBoundaryConstraint(
        ExecutionStepId upstreamUnitId,
        ExecutionStepId downstreamUnitId,
        CompositionBlocker blocker) {

    public CompositionBoundaryConstraint {
        Objects.requireNonNull(upstreamUnitId, "upstreamUnitId");
        Objects.requireNonNull(downstreamUnitId, "downstreamUnitId");
        Objects.requireNonNull(blocker, "blocker");
        if (upstreamUnitId.equals(downstreamUnitId)) {
            throw new IllegalArgumentException("composition boundary must join distinct units");
        }
        if (blocker == CompositionBlocker.MANDATORY_INTERMEDIATE_ARTIFACT
                || blocker == CompositionBlocker.PROVIDER_NATIVE_PIPELINE_UNSUPPORTED
                || blocker == CompositionBlocker.UNKNOWN_PROVIDER_COMPOSITION_SEMANTICS) {
            throw new IllegalArgumentException(
                    "blocker is evaluator-derived and cannot be asserted as a boundary constraint");
        }
    }

    String canonicalKey() {
        return upstreamUnitId.value() + "\u0000" + downstreamUnitId.value() + "\u0000" + blocker.name();
    }
}
