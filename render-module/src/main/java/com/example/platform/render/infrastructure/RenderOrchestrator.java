package com.example.platform.render.infrastructure;

import com.example.platform.render.domain.renderplan.RenderExtent;
import com.example.platform.render.infrastructure.remotion.RenderExecutionTrace;

/**
 * Render orchestrator - the external unified entry point.
 * Orchestrates render jobs through the planner and provider dispatch.
 *
 * <p>PRE-#21 C10/C11 (final exactness): authoritative execution results
 * distinguish a typed REQUESTED_RENDER_EXTENT from a typed ACHIEVED_RENDER_EXTENT
 * (both {@link RenderExtent}; typed render-plan extent is the single extent
 * authority — no String extent representation exists).
 *
 * <p>Failure identity is TYPED: {@link RenderResultFailureReason} is the
 * semantic authority; the human-readable detail string is explanation only
 * (never used for semantic branching).
 *
 * <p>Invariants (enforced by compact constructor):
 * success == true  → failureReason == null
 * success == false → failureReason != null
 *
 * <p>Authoritative extent success requires: requested extent present, achieved
 * extent present, and achieved semantically equal to requested. A success
 * WITHOUT extent proof is ordinary success, never authoritative extent-proven
 * success ({@link #authoritativeSuccess()} is false).
 */
public interface RenderOrchestrator {

    /**
     * Execute a render job.
     * @param job the render job
     * @return the render result
     */
    RenderResult execute(RenderJob job);

    record RenderResult(
            String jobId,
            String artifactId,
            String storageUri,
            long duration,
            String format,
            String resolution,
            boolean success,
            RenderResultFailureReason failureReason,
            String hitReason,
            String providerUsed,
            String chainId,
            String chainVersion,
            RenderExtent requestedRenderExtent,
            RenderExtent achievedRenderExtent,
            RenderExecutionTrace trace
    ) {
        public RenderResult {
            if (success && failureReason != null) {
                throw new IllegalArgumentException(
                        "success result must not carry a failure reason");
            }
            if (!success && failureReason == null) {
                throw new IllegalArgumentException(
                        "failed result must carry a typed failure reason");
            }
        }

        /** Typed failure: semantic reason + explanatory detail. */
        public static RenderResult failed(String jobId, RenderResultFailureReason reason, String detail) {
            return new RenderResult(jobId, null, null, 0, null, null,
                    false, reason, detail, null, null, null, null, null, null);
        }

        /** Typed failure with execution trace. */
        public static RenderResult failed(String jobId, RenderResultFailureReason reason,
                                          String detail, RenderExecutionTrace trace) {
            return new RenderResult(jobId, null, null, 0, null, null,
                    false, reason, detail, null, null, null, null, null, trace);
        }

        /**
         * Fail-closed authoritative success (typed extent + typed failure).
         *
         * <p>When a requested extent is supplied, the achieved extent MUST be
         * present and semantically equal; otherwise the result is a typed
         * failure — RENDER_EXTENT_UNPROVEN when achieved is absent,
         * RENDER_EXTENT_NOT_ACHIEVED when achieved differs.
         * When no requested extent exists, the result is ordinary success
         * (never authoritative extent-proven success).
         */
        public static RenderResult success(
                String jobId, String artifactId, String storageUri,
                long duration, String format, String resolution,
                String providerUsed, String chainId, String chainVersion,
                String hitReason, RenderExtent requestedRenderExtent,
                RenderExtent achievedRenderExtent, RenderExecutionTrace trace) {
            if (requestedRenderExtent != null) {
                if (achievedRenderExtent == null) {
                    return new RenderResult(jobId, null, null, 0, null, null,
                            false, RenderResultFailureReason.RENDER_EXTENT_UNPROVEN,
                            "Render extent not proven: requested=" + requestedRenderExtent
                                    + " achieved=<none>",
                            null, null, null, requestedRenderExtent, null, trace);
                }
                if (!requestedRenderExtent.equals(achievedRenderExtent)) {
                    return new RenderResult(jobId, null, null, 0, null, null,
                            false, RenderResultFailureReason.RENDER_EXTENT_NOT_ACHIEVED,
                            "Render extent not achieved: requested=" + requestedRenderExtent
                                    + " achieved=" + achievedRenderExtent,
                            null, null, null, requestedRenderExtent, achievedRenderExtent, trace);
                }
            }
            return new RenderResult(jobId, artifactId, storageUri, duration, format, resolution,
                    true, null, hitReason, providerUsed, chainId, chainVersion,
                    requestedRenderExtent, achievedRenderExtent, trace);
        }

        /**
         * True only when the result is a success whose requested extent was
         * actually proven by an equal achieved extent. Ordinary success
         * without extent proof returns false — it must never be treated as
         * authoritative extent conformance.
         */
        public boolean authoritativeSuccess() {
            return success && requestedRenderExtent != null
                    && achievedRenderExtent != null
                    && requestedRenderExtent.equals(achievedRenderExtent);
        }
    }
}
