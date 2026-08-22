package com.example.platform.render.infrastructure;

import com.example.platform.render.domain.renderplan.RenderExtent;
import com.example.platform.render.infrastructure.remotion.RenderExecutionTrace;

/**
 * Render orchestrator - the external unified entry point.
 * Orchestrates render jobs through the planner and provider dispatch.
 *
 * <p>PRE-#21 C10/C11 (corrected): authoritative execution results distinguish
 * a typed REQUESTED_RENDER_EXTENT from a typed ACHIEVED_RENDER_EXTENT
 * (both {@link RenderExtent}; the typed render-plan extent is the single
 * extent authority — no String extent representation exists).
 *
 * <p>Authoritative extent success requires: requested extent present, achieved
 * extent present, and achieved semantically equal to requested. A success
 * WITHOUT extent proof is ordinary success, never authoritative
 * extent-proven success ({@link #authoritativeSuccess()} is false).
 * Insufficient/unproven extent yields a typed failure result via
 * {@link #success} (fail-closed).
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
            String providerUsed,
            String chainId,
            String chainVersion,
            String hitReason,
            RenderExtent requestedRenderExtent,
            RenderExtent achievedRenderExtent,
            RenderExecutionTrace trace
    ) {
        public static RenderResult failed(String jobId, String error) {
            return new RenderResult(jobId, null, null, 0, null, null,
                    false, null, null, null, error, null, null, null);
        }

        public static RenderResult failed(String jobId, String error, RenderExecutionTrace trace) {
            return new RenderResult(jobId, null, null, 0, null, null,
                    false, null, null, null, error, null, null, trace);
        }

        /**
         * Fail-closed authoritative success (typed extent).
         *
         * <p>When a requested extent is supplied, the achieved extent MUST be
         * present and semantically equal; otherwise the result is a typed
         * failure (AUTHORITATIVE_FRAME_OUTPUT_SILENT_PARTIAL_COUNT = 0,
         * AUTHORITATIVE_SUCCESS_WITHOUT_EXTENT_PROOF_COUNT = 0).
         * When no requested extent exists, the result is ordinary success
         * (never authoritative extent-proven success).
         */
        public static RenderResult success(
                String jobId, String artifactId, String storageUri,
                long duration, String format, String resolution,
                String providerUsed, String chainId, String chainVersion,
                String hitReason, RenderExtent requestedRenderExtent,
                RenderExtent achievedRenderExtent, RenderExecutionTrace trace) {
            boolean extentProven = requestedRenderExtent != null
                    && achievedRenderExtent != null
                    && requestedRenderExtent.equals(achievedRenderExtent);
            if (requestedRenderExtent != null && !extentProven) {
                return new RenderResult(jobId, null, null, 0, null, null,
                        false, null, null, null,
                        "Render extent not achieved: requested=" + requestedRenderExtent
                                + " achieved=" + achievedRenderExtent,
                        requestedRenderExtent, achievedRenderExtent, trace);
            }
            return new RenderResult(jobId, artifactId, storageUri, duration, format, resolution,
                    true, providerUsed, chainId, chainVersion, hitReason,
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
