package com.example.platform.render.domain.timeline.diff.merge;

/**
 * Identifier for a merge conflict analysis. Internal domain model.
 */
public record TimelineMergeConflictAnalysisId(String value) {
    public TimelineMergeConflictAnalysisId {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("TimelineMergeConflictAnalysisId must not be blank");
    }
}
