package com.example.platform.render.app.visibility;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Read-only #20 boundary audit: never replaces or reseals the original plan. */
public record VisibilityPlanningAudit(
        String originalPlanFingerprint,
        String originalPlanId,
        Optional<TemporalVisibilityAnalysis> analysis,
        boolean graphChanged,
        String proofDigest,
        List<String> reasons) {

    public VisibilityPlanningAudit {
        Objects.requireNonNull(originalPlanFingerprint, "originalPlanFingerprint");
        Objects.requireNonNull(originalPlanId, "originalPlanId");
        Objects.requireNonNull(analysis, "analysis");
        Objects.requireNonNull(proofDigest, "proofDigest");
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
        if (graphChanged) {
            throw new IllegalArgumentException("R2B audit cannot mutate the graph");
        }
    }
}
