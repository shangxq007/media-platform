package com.example.platform.render.domain.scenario;

import java.util.Map;
import java.util.Objects;

/**
 * Issue observed during scenario execution.
 * Immutable record. Internal domain model.
 */
public record InternalScenarioIssue(
        InternalScenarioIssueSeverity severity,
        InternalScenarioIssueCode code,
        String message,
        Map<String, String> safeMetadata) {

    public InternalScenarioIssue {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
    }

    public static InternalScenarioIssue info(InternalScenarioIssueCode code, String message) {
        return new InternalScenarioIssue(InternalScenarioIssueSeverity.INFO, code, message, Map.of());
    }

    public static InternalScenarioIssue warning(InternalScenarioIssueCode code, String message) {
        return new InternalScenarioIssue(InternalScenarioIssueSeverity.WARNING, code, message, Map.of());
    }

    public static InternalScenarioIssue error(InternalScenarioIssueCode code, String message) {
        return new InternalScenarioIssue(InternalScenarioIssueSeverity.ERROR, code, message, Map.of());
    }

    public static InternalScenarioIssue blocking(InternalScenarioIssueCode code, String message) {
        return new InternalScenarioIssue(InternalScenarioIssueSeverity.BLOCKING, code, message, Map.of());
    }
}
