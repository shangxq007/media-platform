package com.example.platform.render.api.port;

/**
 * Optional hook to register browser-exported files in the artifact catalog.
 */
public interface ClientExportArtifactPort {

    RegisteredArtifact register(
            String sessionId,
            String projectId,
            String storageUri,
            String format,
            String resolution,
            long durationSeconds);

    RegisteredArtifact register(
            String sessionId,
            String projectId,
            String storageUri,
            String format,
            String resolution,
            long durationSeconds,
            Long sizeBytes,
            String checksum);

    record RegisteredArtifact(String artifactId, String storageUri, String downloadPath) {}
}
