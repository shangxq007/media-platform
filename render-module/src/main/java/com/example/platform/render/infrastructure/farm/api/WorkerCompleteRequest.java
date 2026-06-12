package com.example.platform.render.infrastructure.farm.api;

/**
 * Request from a worker when a job completes successfully.
 */
public record WorkerCompleteRequest(
        String jobId,
        int attempt,
        String providerId,
        String artifactUri,
        String manifestUri,
        String logsUri,
        String thumbnailUri,
        String checksum,
        Long durationMs,
        String mediaInfoJson,
        String warnings
) {}
