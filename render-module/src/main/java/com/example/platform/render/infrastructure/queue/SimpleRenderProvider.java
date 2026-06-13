package com.example.platform.render.infrastructure.queue;

/**
 * Simplified render provider interface for production.
 * 
 * <p>Only FFmpeg is supported in production mode.
 * No fallback logic, no capability negotiation.
 */
public interface SimpleRenderProvider {

    /**
     * Execute a render job.
     */
    RenderResult execute(RenderRequest request);

    /**
     * Get the provider name.
     */
    String getName();

    /**
     * Check if the provider is available.
     */
    boolean isAvailable();

    // ---------------------------------------------------------------------------
    // Inner Types
    // ---------------------------------------------------------------------------

    record RenderRequest(
            String jobId,
            String profile,
            String aiScript,
            String tenantId
    ) {}

    record RenderResult(
            String jobId,
            String artifactId,
            String storageUri,
            long durationMs,
            boolean success,
            String error
    ) {
        public static RenderResult success(String jobId, String artifactId, String storageUri, long durationMs) {
            return new RenderResult(jobId, artifactId, storageUri, durationMs, true, null);
        }

        public static RenderResult failure(String jobId, String error) {
            return new RenderResult(jobId, null, null, 0, false, error);
        }
    }
}
