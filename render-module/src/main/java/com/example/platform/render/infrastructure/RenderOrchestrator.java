package com.example.platform.render.infrastructure;

import com.example.platform.render.infrastructure.remotion.RenderExecutionTrace;

/**
 * Render orchestrator - the external unified entry point.
 * Orchestrates render jobs through the planner and provider dispatch.
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
            RenderExecutionTrace trace
    ) {
        public static RenderResult failed(String jobId, String error) {
            return new RenderResult(jobId, null, null, 0, null, null,
                    false, null, null, null, error, null);
        }

        public static RenderResult failed(String jobId, String error, RenderExecutionTrace trace) {
            return new RenderResult(jobId, null, null, 0, null, null,
                    false, null, null, null, error, trace);
        }
    }
}
