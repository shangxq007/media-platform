package com.example.platform.render.domain.scenario;

import java.util.List;
import java.util.Map;

/**
 * Actual outcome of a scenario execution.
 * Immutable record. Internal domain model.
 */
public record InternalScenarioActualOutcome(
        InternalScenarioResultStatus actualStatus,
        List<InternalScenarioIssueCode> actualIssueCodes,
        Map<String, Object> actualPlanProperties,
        List<InternalScenarioIssue> issues,
        Map<String, String> safeMetadata) {

    public static InternalScenarioActualOutcome of(
            InternalScenarioResultStatus status,
            List<InternalScenarioIssueCode> issueCodes,
            Map<String, Object> planProperties,
            List<InternalScenarioIssue> issues) {
        return new InternalScenarioActualOutcome(
                status,
                issueCodes == null ? List.of() : issueCodes,
                planProperties == null ? Map.of() : planProperties,
                issues == null ? List.of() : issues,
                Map.of());
    }
}
