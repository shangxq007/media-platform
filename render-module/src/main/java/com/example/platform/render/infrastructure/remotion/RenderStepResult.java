package com.example.platform.render.infrastructure.remotion;

import java.time.Instant;
import java.util.List;

public record RenderStepResult(
        String stepId,
        String providerName,
        String providerType,
        String status,
        String inputHash,
        List<RenderArtifact> outputArtifacts,
        List<String> logs,
        List<String> warnings,
        List<String> errors,
        long durationMs,
        boolean fallbackUsed,
        Instant startedAt,
        Instant finishedAt
) {
    public boolean isSuccess() {
        return "COMPLETED".equals(status);
    }

    public boolean isFailed() {
        return "FAILED".equals(status);
    }
}
