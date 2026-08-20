package com.example.platform.render.domain.renderplan;

import java.util.Objects;

/**
 * Explanatory/reproducibility provenance (C26). NOT canonical authored
 * authority; NOT a fingerprint input.
 *
 * <p>R4-A4: provenance explains the authored inputs consumed by the plan:
 * the verified Timeline revision identity (via {@code timelineRevisionId})
 * and the authoritative Effect semantic reference/content pin
 * (via {@code effectSemanticReference}). These fields are EXPLANATORY —
 * they repeat the semantic inputs that already participate in the canonical
 * fingerprint through {@link RenderPlan#revision()} and
 * {@link RenderPlan#effectSemanticReference()}; changing provenance metadata
 * alone does NOT change the plan fingerprint.
 *
 * @param plannerFormatVersion  the planner/format version that produced the plan
 * @param timelineRevisionId    the verified Timeline revision identity planned
 * @param effectSemanticReference the authoritative authored Effect semantic
 *                              reference (contract version + content pin)
 */
public record RenderPlanProvenance(
        String plannerFormatVersion,
        String timelineRevisionId,
        EffectSemanticReference effectSemanticReference) {

    public RenderPlanProvenance {
        Objects.requireNonNull(plannerFormatVersion, "plannerFormatVersion");
        Objects.requireNonNull(timelineRevisionId, "timelineRevisionId");
        Objects.requireNonNull(effectSemanticReference, "effectSemanticReference");
    }
}
