package com.example.platform.render.domain.artifact;

/**
 * Boundary policy for Artifact DAG evaluation.
 *
 * <p>Controls how Artifact DAG interacts with render decisions.
 * The policy enforces that Artifact DAG is optional, deferred, and non-blocking
 * by default.</p>
 */
public record ArtifactDagBoundaryPolicy(
        ArtifactDagMode mode,
        boolean allowProviderBindingInfluence,
        boolean allowRenderExecutionInfluence
) {
    /**
     * Default policy: DISABLED mode, no influence on provider binding or render execution.
     */
    public static ArtifactDagBoundaryPolicy defaultPolicy() {
        return new ArtifactDagBoundaryPolicy(ArtifactDagMode.DISABLED, false, false);
    }

    /**
     * Create policy for the given mode with default boundary settings.
     */
    public static ArtifactDagBoundaryPolicy forMode(ArtifactDagMode mode) {
        return switch (mode) {
            case DISABLED -> defaultPolicy();
            case DRY_RUN -> new ArtifactDagBoundaryPolicy(mode, false, false);
            case EXPERIMENTAL -> new ArtifactDagBoundaryPolicy(mode, false, false);
            case REQUIRED -> new ArtifactDagBoundaryPolicy(mode, true, true);
        };
    }

    /**
     * Returns true if Artifact DAG evaluation should be skipped entirely.
     */
    public boolean shouldSkip() {
        return mode == ArtifactDagMode.DISABLED;
    }

    /**
     * Returns true if Artifact DAG can build for analysis (dry-run or higher).
     */
    public boolean allowsAnalysis() {
        return mode.allowsAnalysis();
    }

    /**
     * Returns true if Artifact DAG can affect provider binding.
     * Only true for REQUIRED mode.
     */
    public boolean canAffectProviderBinding() {
        return allowProviderBindingInfluence && mode.canAffectRenderDecisions();
    }

    /**
     * Returns true if Artifact DAG can affect render execution plan.
     * Only true for REQUIRED mode.
     */
    public boolean canAffectRenderExecution() {
        return allowRenderExecutionInfluence && mode.canAffectRenderDecisions();
    }
}
