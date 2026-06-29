package com.example.platform.render.domain.scenario;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Result of a single scenario execution.
 * Immutable record. Internal domain model.
 * Deterministic ordering: issues sorted by severity then code.
 */
public record InternalScenarioResult(
        InternalScenarioId scenarioId,
        InternalScenarioName scenarioName,
        InternalScenarioCategory category,
        InternalScenarioResultStatus status,
        InternalScenarioExpectedOutcome expectedOutcome,
        InternalScenarioActualOutcome actualOutcome,
        List<InternalScenarioIssue> issues,
        Map<String, String> safeMetadata) {

    public InternalScenarioResult {
        Objects.requireNonNull(scenarioId, "scenarioId");
        Objects.requireNonNull(scenarioName, "scenarioName");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(expectedOutcome, "expectedOutcome");
        Objects.requireNonNull(actualOutcome, "actualOutcome");
        issues = issues == null ? List.of() : List.copyOf(issues);
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
    }

    public boolean passed() {
        return status == InternalScenarioResultStatus.PASS
                || status == InternalScenarioResultStatus.PASS_WITH_WARNINGS;
    }
}
