package com.example.platform.render.domain.timeline.diff.merge.plan;

import java.util.Map;

/**
 * Issue found during merge plan generation.
 * Internal domain model. No stack traces, no provider/storage details.
 */
public record TimelineMergePlanIssue(
        TimelineMergePlanIssueSeverity severity,
        TimelineMergePlanIssueCode code,
        String field,
        String message,
        Map<String, String> safeMetadata) {

    public TimelineMergePlanIssue {
        if (severity == null) throw new IllegalArgumentException("Severity must not be null");
        if (code == null) throw new IllegalArgumentException("Code must not be null");
    }

    public static TimelineMergePlanIssue of(
            TimelineMergePlanIssueSeverity severity,
            TimelineMergePlanIssueCode code,
            String field,
            String message) {
        return new TimelineMergePlanIssue(severity, code, field, message, Map.of());
    }
}
