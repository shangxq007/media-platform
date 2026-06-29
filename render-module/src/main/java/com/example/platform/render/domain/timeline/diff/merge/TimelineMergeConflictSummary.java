package com.example.platform.render.domain.timeline.diff.merge;

import java.util.Map;

/**
 * Summary of merge conflict analysis. Internal domain model.
 * Provider-neutral, storage-neutral.
 */
public record TimelineMergeConflictSummary(
        int oursOperationCount,
        int theirsOperationCount,
        int conflictCount,
        int blockingConflictCount,
        int manualReviewConflictCount,
        Map<String, String> safeMetadata) {

    public static TimelineMergeConflictSummary of(
            int oursOps, int theirsOps, int conflicts, int blocking, int manualReview) {
        return new TimelineMergeConflictSummary(
                oursOps, theirsOps, conflicts, blocking, manualReview, Map.of());
    }
}
