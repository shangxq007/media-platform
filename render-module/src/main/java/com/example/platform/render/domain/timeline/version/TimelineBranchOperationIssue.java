package com.example.platform.render.domain.timeline.version;

import java.util.Map;

/**
 * Issue from a branch operation.
 * Internal domain model. No provider/storage internals.
 */
public record TimelineBranchOperationIssue(
        TimelineBranchOperationIssueSeverity severity,
        TimelineBranchOperationIssueCode code,
        String message,
        Map<String, String> safeMetadata) {

    public TimelineBranchOperationIssue {
        if (severity == null) throw new IllegalArgumentException("Severity must not be null");
        if (code == null) throw new IllegalArgumentException("Code must not be null");
        if (safeMetadata == null) safeMetadata = Map.of();
        else safeMetadata = Map.copyOf(safeMetadata);
    }
}
