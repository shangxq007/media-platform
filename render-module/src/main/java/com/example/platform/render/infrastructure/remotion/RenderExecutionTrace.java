package com.example.platform.render.infrastructure.remotion;

import java.time.Instant;
import java.util.List;

public record RenderExecutionTrace(
        String jobId,
        String jobType,
        String mode,
        List<RenderStepResult> stepResults,
        List<RenderArtifact> allArtifacts,
        boolean overallSuccess,
        boolean fallbackOccurred,
        Instant startedAt,
        Instant finishedAt
) {
    public List<RenderStepResult> failedSteps() {
        return stepResults.stream().filter(RenderStepResult::isFailed).toList();
    }

    public List<RenderArtifact> artifactsByType(RenderArtifactType type) {
        return allArtifacts.stream().filter(a -> a.artifactType() == type).toList();
    }
}
