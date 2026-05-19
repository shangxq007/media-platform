package com.example.platform.prompt.domain;

import java.time.OffsetDateTime;

/**
 * Record of a prompt execution run.
 */
public record PromptExecutionRun(
        String executionId,
        String templateId,
        String promptVersion,
        String tenantId,
        String userId,
        String modelProvider,
        String modelName,
        String renderedPromptHash,
        String redactedPromptPreview,
        String inputVariablesRedactedJson,
        String outputSummary,
        PromptExecutionStatus status,
        PromptRiskLevel riskLevel,
        int tokenEstimate,
        double costEstimate,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        String errorCode,
        String errorDetailsJson,
        String relatedPromptFile,
        String relatedManifestEntry) {
}
