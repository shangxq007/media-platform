package com.example.platform.render.infrastructure;

import com.example.platform.render.infrastructure.remotion.RenderExecutionTrace;

/**
 * Render orchestrator - the external unified entry point.
 * Orchestrates render jobs through the planner and provider dispatch.
 *
 * <p>PRE-#21 W4 (C10/C11): authoritative execution results distinguish
 * REQUESTED_RENDER_EXTENT from ACHIEVED_RENDER_EXTENT. A success result is
 * only authoritative when the achieved extent satisfies the requested extent
 * (fail-closed): insufficient extent never yields authoritative success.
 * The {@link #success} factory is the only way to construct an
 * authoritative-success result and enforces this invariant.
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
            String requestedExtent,
            String achievedExtent,
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
         * Fail-closed authoritative success.
         *
         * <p>When a requested extent is supplied, the achieved extent MUST
         * satisfy it; otherwise the result is a typed failure
         * (AUTHORITATIVE_FRAME_OUTPUT_SILENT_PARTIAL_COUNT = 0).
         * Extent values use canonical form (start,end,frameRate).
         */
        public static RenderResult success(
                String jobId, String artifactId, String storageUri,
                long duration, String format, String resolution,
                String providerUsed, String chainId, String chainVersion,
                String hitReason, String requestedExtent, String achievedExtent,
                RenderExecutionTrace trace) {
            boolean extentSatisfied = requestedExtent == null
                    || (achievedExtent != null && requestedExtent.equals(achievedExtent));
            if (!extentSatisfied) {
                return new RenderResult(jobId, null, null, 0, null, null,
                        false, null, null, null,
                        "Render extent not achieved: requested=" + requestedExtent
                                + " achieved=" + achievedExtent,
                        requestedExtent, achievedExtent, trace);
            }
            return new RenderResult(jobId, artifactId, storageUri, duration, format, resolution,
                    true, providerUsed, chainId, chainVersion, hitReason,
                    requestedExtent, achievedExtent, trace);
        }

        /** True only when the result is a success whose extent, if requested, was achieved. */
        public boolean authoritativeSuccess() {
            return success && (requestedExtent == null
                    || (achievedExtent != null && requestedExtent.equals(achievedExtent)));
        }
    }
}
