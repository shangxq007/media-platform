package com.example.platform.render.domain.scenario;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Expected outcome of a scenario execution.
 * Immutable record. Internal domain model.
 */
public record InternalScenarioExpectedOutcome(
        InternalScenarioResultStatus expectedStatus,
        List<InternalScenarioIssueCode> expectedIssueCodes,
        Map<String, Object> expectedPlanProperties,
        Map<String, String> safeMetadata) {

    public InternalScenarioExpectedOutcome {
        Objects.requireNonNull(expectedStatus, "expectedStatus");
        expectedIssueCodes = expectedIssueCodes == null ? List.of() : List.copyOf(expectedIssueCodes);
        expectedPlanProperties = expectedPlanProperties == null ? Map.of() : Map.copyOf(expectedPlanProperties);
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
    }
}
