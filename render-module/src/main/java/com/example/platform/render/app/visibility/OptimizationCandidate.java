package com.example.platform.render.app.visibility;

import java.util.List;
import java.util.Objects;

/** A future-plan candidate; R2A never makes it executable or mutates a graph. */
public record OptimizationCandidate(
        String clipId,
        String kind,
        boolean productionApplicable,
        List<String> blockers) {

    public OptimizationCandidate {
        Objects.requireNonNull(clipId, "clipId");
        Objects.requireNonNull(kind, "kind");
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
    }
}
