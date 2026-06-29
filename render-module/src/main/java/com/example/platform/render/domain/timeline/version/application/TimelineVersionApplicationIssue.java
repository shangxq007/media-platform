package com.example.platform.render.domain.timeline.version.application;

import java.util.Map;

/**
 * Issue from a version application operation.
 * Internal domain model. No provider/storage internals.
 */
public record TimelineVersionApplicationIssue(
        TimelineVersionApplicationIssueSeverity severity,
        TimelineVersionApplicationIssueCode code,
        String message,
        Map<String, String> safeMetadata
) {
    public TimelineVersionApplicationIssue {
        if (severity == null) throw new IllegalArgumentException("Severity must not be null");
        if (code == null) throw new IllegalArgumentException("Code must not be null");
        if (safeMetadata == null) safeMetadata = Map.of();
        else safeMetadata = Map.copyOf(safeMetadata);
    }
}
