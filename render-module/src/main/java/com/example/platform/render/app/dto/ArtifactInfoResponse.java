package com.example.platform.render.app.dto;

public record ArtifactInfoResponse(
        String artifactId,
        String renderJobId,
        String projectId,
        String storageUri,
        String format,
        String resolution,
        long duration,
        java.time.Instant createdAt) {}
