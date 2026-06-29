package com.example.platform.render.domain.artifact;

/**
 * Runtime mode for Artifact DAG evaluation.
 *
 * <p>Artifact DAG is a deferred render optimization layer for incremental render,
 * cache reuse, artifact lineage, and partial recomputation. It is not required for
 * Timeline Git, rollback, branch switching, merge preview, template workflow MVP,
 * or explicit full render.</p>
 *
 * <p>Default mode is {@link #DISABLED}. Artifact DAG must not block render,
 * must not drive provider binding, and must not drive render execution planning
 * by default.</p>
 */
public enum ArtifactDagMode {

    /**
     * Artifact DAG is disabled. Do not build, use, or block render on Artifact DAG.
     * Audit/metadata may record artifactDagMode=DISABLED and artifactDagImpact=NOT_COMPUTED.
     */
    DISABLED,

    /**
     * Artifact DAG may be built for internal analysis only.
     * Result may be logged or recorded in internal audit metadata.
     * Must not affect provider binding or render execution plan.
     * Must not block render. Must not expose public API.
     */
    DRY_RUN,

    /**
     * Internal-only, feature-flagged. Not default, not public, not product-required.
     * May be used only in tests or controlled experiments.
     * Must not become default path.
     */
    EXPERIMENTAL,

    /**
     * Future-only. Must not be enabled by default.
     * If enum value is present, tests must prove it is not default.
     * When Artifact DAG is ready for production, this mode enables full evaluation.
     */
    REQUIRED;

    /**
     * Returns true if this mode allows building the Artifact DAG for analysis.
     */
    public boolean allowsAnalysis() {
        return this == DRY_RUN || this == EXPERIMENTAL || this == REQUIRED;
    }

    /**
     * Returns true if this mode allows Artifact DAG to affect render decisions.
     * Only REQUIRED mode can affect render decisions, and it is not enabled by default.
     */
    public boolean canAffectRenderDecisions() {
        return this == REQUIRED;
    }

    /**
     * Returns the default mode (DISABLED).
     */
    public static ArtifactDagMode defaultMode() {
        return DISABLED;
    }
}
