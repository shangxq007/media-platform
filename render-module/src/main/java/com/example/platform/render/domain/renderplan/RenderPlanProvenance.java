package com.example.platform.render.domain.renderplan;

import java.util.Objects;

/**
 * HOOK: explanatory/reproducibility provenance (C26). NOT canonical authored
 * authority; NOT a fingerprint input.
 *
 * @param plannerFormatVersion the planner/format version that produced the plan
 */
public record RenderPlanProvenance(String plannerFormatVersion) {

    public RenderPlanProvenance {
        Objects.requireNonNull(plannerFormatVersion, "plannerFormatVersion");
    }
}
