package com.example.platform.remoterender.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a render job submitted to a remote worker.
 */
public record RemoteRenderJob(
        String jobId,
        String workerId,
        String status,
        String profile,
        String timelineJson,
        String aiScript,
        String outputPath,
        String artifactId,
        String storageUri,
        String errorCode,
        String errorMessage,
        Map<String, String> metadata,
        Instant submittedAt,
        Instant startedAt,
        Instant completedAt
) {
    public static RemoteRenderJob create(String workerId, String profile, String timelineJson) {
        return new RemoteRenderJob(
                UUID.randomUUID().toString(),
                workerId,
                "SUBMITTED",
                profile,
                timelineJson,
                null,
                null,
                null,
                null,
                null,
                null,
                Map.of(),
                Instant.now(),
                null,
                null
        );
    }

    public RemoteRenderJob withStatus(String newStatus) {
        return new RemoteRenderJob(jobId, workerId, newStatus, profile, timelineJson, aiScript,
                outputPath, artifactId, storageUri, errorCode, errorMessage, metadata,
                submittedAt, startedAt, completedAt);
    }

    public RemoteRenderJob withAiScript(String script) {
        return new RemoteRenderJob(jobId, workerId, status, profile, timelineJson, script,
                outputPath, artifactId, storageUri, errorCode, errorMessage, metadata,
                submittedAt, startedAt, completedAt);
    }

    public RemoteRenderJob withStarted() {
        return new RemoteRenderJob(jobId, workerId, "RUNNING", profile, timelineJson, aiScript,
                outputPath, artifactId, storageUri, errorCode, errorMessage, metadata,
                submittedAt, Instant.now(), completedAt);
    }

    public RemoteRenderJob withCompleted(String artifact, String uri) {
        return new RemoteRenderJob(jobId, workerId, "COMPLETED", profile, timelineJson, aiScript,
                outputPath, artifact, uri, null, null, metadata,
                submittedAt, startedAt, Instant.now());
    }

    public RemoteRenderJob withFailed(String code, String message) {
        return new RemoteRenderJob(jobId, workerId, "FAILED", profile, timelineJson, aiScript,
                outputPath, artifactId, storageUri, code, message, metadata,
                submittedAt, startedAt, Instant.now());
    }
}
