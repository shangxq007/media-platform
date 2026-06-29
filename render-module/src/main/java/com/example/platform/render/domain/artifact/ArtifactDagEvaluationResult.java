package com.example.platform.render.domain.artifact;

import com.example.platform.render.domain.timeline.diff.ArtifactDAGImpact;
import java.util.List;
import java.util.Map;

/**
 * Result of an Artifact DAG evaluation.
 *
 * <p>Carries the evaluation status, optional artifact graph, impact analysis,
 * and safe metadata. Does not carry provider/storage internals.</p>
 *
 * <p>When mode is DISABLED, status is SKIPPED_DISABLED and no graph is computed.
 * When mode is DRY_RUN, failure produces FAILED_NON_BLOCKING status.
 * When mode is REQUIRED, failure may produce FAILED_NON_BLOCKING or throw.</p>
 */
public record ArtifactDagEvaluationResult(
        ArtifactDagMode mode,
        ArtifactDagEvaluationStatus status,
        ArtifactGraph artifactGraph,
        ArtifactDAGImpact impact,
        List<String> issues,
        Map<String, String> safeMetadata
) {
    /**
     * Create a disabled result (no computation).
     */
    public static ArtifactDagEvaluationResult disabled() {
        return new ArtifactDagEvaluationResult(
                ArtifactDagMode.DISABLED,
                ArtifactDagEvaluationStatus.SKIPPED_DISABLED,
                null, null, List.of(), Map.of());
    }

    /**
     * Create a not-computed result.
     */
    public static ArtifactDagEvaluationResult notComputed(ArtifactDagMode mode) {
        return new ArtifactDagEvaluationResult(
                mode, ArtifactDagEvaluationStatus.NOT_COMPUTED,
                null, null, List.of(), Map.of());
    }

    /**
     * Create a dry-run completed result with optional graph.
     */
    public static ArtifactDagEvaluationResult dryRunCompleted(ArtifactGraph graph, ArtifactDAGImpact impact) {
        return new ArtifactDagEvaluationResult(
                ArtifactDagMode.DRY_RUN,
                ArtifactDagEvaluationStatus.DRY_RUN_COMPLETED,
                graph, impact, List.of(), Map.of());
    }

    /**
     * Create a non-blocking failure result.
     */
    public static ArtifactDagEvaluationResult failedNonBlocking(List<String> issues) {
        return new ArtifactDagEvaluationResult(
                ArtifactDagMode.DRY_RUN,
                ArtifactDagEvaluationStatus.FAILED_NON_BLOCKING,
                null, null, issues != null ? issues : List.of(), Map.of());
    }

    /**
     * Returns true if the evaluation was skipped (mode disabled).
     */
    public boolean wasSkipped() {
        return status == ArtifactDagEvaluationStatus.SKIPPED_DISABLED;
    }

    /**
     * Returns true if the evaluation completed (dry-run or experimental).
     */
    public boolean wasComputed() {
        return status.wasComputed();
    }

    /**
     * Returns true if the evaluation failed non-blockingly.
     */
    public boolean isNonBlockingFailure() {
        return status.isNonBlockingFailure();
    }

    /**
     * Returns true if the evaluation can affect render decisions.
     * Only REQUIRED mode with successful computation can affect decisions.
     */
    public boolean canAffectRenderDecisions() {
        return mode.canAffectRenderDecisions() && status.wasComputed();
    }
}
