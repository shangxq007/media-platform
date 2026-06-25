package com.example.platform.render.domain.timeline.internal;

import java.util.List;

/**
 * Result of a three-way timeline merge operation.
 */
public record TimelineMergeResult(
        MergeStatus status,
        String baseRevisionId,
        String sourceRevisionId,
        String targetRevisionId,
        String mergedRevisionId,
        List<SemanticChange> autoMergedChanges,
        List<TimelineConflict> conflicts,
        TimelineMergeSummary mergeSummary,
        String summary,
        String mergedPayloadJson) {

    public enum MergeStatus {
        MERGED,
        CONFLICTS,
        NO_OP,
        FAILED
    }

    public boolean hasConflicts() {
        return conflicts != null && !conflicts.isEmpty();
    }

    public boolean isMerged() {
        return status == MergeStatus.MERGED;
    }
}
