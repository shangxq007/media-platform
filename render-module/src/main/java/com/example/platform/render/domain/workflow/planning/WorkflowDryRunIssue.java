package com.example.platform.render.domain.workflow.planning;

import java.util.Map;

/**
 * Issue found during workflow dry-run planning.
 * Internal domain model. No stack traces, no provider/storage details.
 */
public record WorkflowDryRunIssue(
        WorkflowDryRunIssueSeverity severity,
        WorkflowDryRunIssueCode code,
        String field,
        String message,
        Map<String, String> safeMetadata) {

    public WorkflowDryRunIssue {
        if (severity == null) throw new IllegalArgumentException("Severity must not be null");
        if (code == null) throw new IllegalArgumentException("Code must not be null");
    }

    public boolean isBlocking() {
        return severity == WorkflowDryRunIssueSeverity.BLOCKING;
    }
}
