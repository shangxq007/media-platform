package com.example.platform.prompt.domain;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Result of prompt evaluation.
 */
public record PromptEvaluationResult(
        String evaluationId,
        String executionId,
        String templateId,
        String evaluatorUserId,
        boolean acceptanceCriteriaMet,
        boolean documentationUpdated,
        boolean manifestUpdated,
        boolean testsPass,
        boolean hasHighRiskChanges,
        boolean hasHumanReviewItems,
        boolean hasScopeCreep,
        boolean hasFalseClaims,
        String overallVerdict,
        Map<String, String> dimensionScores,
        OffsetDateTime evaluatedAt) {
}
