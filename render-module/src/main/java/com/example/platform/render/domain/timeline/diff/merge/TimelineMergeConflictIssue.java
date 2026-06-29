package com.example.platform.render.domain.timeline.diff.merge;

import java.util.Map;

/**
 * Issue found during merge conflict analysis.
 * Internal domain model. No stack traces, no provider/storage details.
 */
public record TimelineMergeConflictIssue(
        TimelineMergeConflictIssueSeverity severity,
        TimelineMergeConflictIssueCode code,
        String field,
        String message,
        Map<String, String> safeMetadata) {

    public TimelineMergeConflictIssue {
        if (severity == null) throw new IllegalArgumentException("Severity must not be null");
        if (code == null) throw new IllegalArgumentException("Code must not be null");
    }
}
