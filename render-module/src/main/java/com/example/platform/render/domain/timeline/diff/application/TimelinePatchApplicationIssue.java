package com.example.platform.render.domain.timeline.diff.application;

import java.util.Map;

/**
 * Issue found during patch application. Internal domain model. No stack traces.
 */
public record TimelinePatchApplicationIssue(
        TimelinePatchApplicationIssueSeverity severity,
        TimelinePatchApplicationIssueCode code,
        String field,
        String message,
        Map<String, String> safeMetadata) {

    public TimelinePatchApplicationIssue {
        if (severity == null) throw new IllegalArgumentException("Severity must not be null");
        if (code == null) throw new IllegalArgumentException("Code must not be null");
    }
}
