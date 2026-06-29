package com.example.platform.render.domain.timeline.editing;

import java.util.Map;
import java.util.Objects;

/**
 * Typed validation issue for timeline validation.
 * Immutable record. Internal domain model.
 *
 * @param severity issue severity
 * @param code     issue code
 * @param field    affected field path (e.g., "tracks[0].clips[1]")
 * @param message  human-readable message
 * @param safeMetadata safe metadata only
 */
public record TimelineValidationIssue(
        TimelineValidationIssueSeverity severity,
        TimelineValidationIssueCode code,
        String field,
        String message,
        Map<String, String> safeMetadata) {

    public TimelineValidationIssue {
        Objects.requireNonNull(severity, "severity must not be null");
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(message, "message must not be null");
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
    }

    public static TimelineValidationIssue error(TimelineValidationIssueCode code, String field, String message) {
        return new TimelineValidationIssue(TimelineValidationIssueSeverity.ERROR, code, field, message, Map.of());
    }

    public static TimelineValidationIssue warning(TimelineValidationIssueCode code, String field, String message) {
        return new TimelineValidationIssue(TimelineValidationIssueSeverity.WARNING, code, field, message, Map.of());
    }

    public static TimelineValidationIssue blocking(TimelineValidationIssueCode code, String field, String message) {
        return new TimelineValidationIssue(TimelineValidationIssueSeverity.BLOCKING, code, field, message, Map.of());
    }

    public static TimelineValidationIssue info(TimelineValidationIssueCode code, String field, String message) {
        return new TimelineValidationIssue(TimelineValidationIssueSeverity.INFO, code, field, message, Map.of());
    }
}
