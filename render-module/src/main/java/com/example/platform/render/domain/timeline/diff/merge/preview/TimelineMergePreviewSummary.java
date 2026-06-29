package com.example.platform.render.domain.timeline.diff.merge.preview;

import java.util.Map;

/**
 * Summary of a merge preview result.
 * Internal domain model. Provider-neutral, storage-neutral.
 */
public record TimelineMergePreviewSummary(
        String baseRevisionId,
        String oursRevisionId,
        String theirsRevisionId,
        int oursOperationCount,
        int theirsOperationCount,
        int conflictCount,
        int blockingConflictCount,
        boolean mergeReady,
        boolean manualReviewRequired,
        Map<String, String> safeMetadata) {

    public static TimelineMergePreviewSummary of(
            String baseRevisionId,
            String oursRevisionId,
            String theirsRevisionId,
            int oursOps,
            int theirsOps,
            int conflicts,
            int blocking,
            boolean mergeReady,
            boolean manualReviewRequired) {
        return new TimelineMergePreviewSummary(
                baseRevisionId, oursRevisionId, theirsRevisionId,
                oursOps, theirsOps, conflicts, blocking,
                mergeReady, manualReviewRequired, Map.of());
    }
}
