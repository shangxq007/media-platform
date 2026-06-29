package com.example.platform.render.domain.timeline.diff.merge.preview;

import java.util.Map;

/**
 * Issue found during merge preview.
 * Internal domain model. No stack traces, no provider/storage details.
 */
public record TimelineMergePreviewIssue(
        TimelineMergePreviewIssueSeverity severity,
        TimelineMergePreviewIssueCode code,
        String field,
        String message,
        Map<String, String> safeMetadata) {

    public TimelineMergePreviewIssue {
        if (severity == null) throw new IllegalArgumentException("Severity must not be null");
        if (code == null) throw new IllegalArgumentException("Code must not be null");
    }

    public static TimelineMergePreviewIssue of(
            TimelineMergePreviewIssueSeverity severity,
            TimelineMergePreviewIssueCode code,
            String field,
            String message) {
        return new TimelineMergePreviewIssue(severity, code, field, message, Map.of());
    }
}
