package com.example.platform.render.domain.timeline.diff.merge.plan;

import java.util.Map;

/**
 * Summary of a merge plan.
 * Internal domain model. Provider-neutral, storage-neutral.
 */
public record TimelineMergePlanSummary(
        int oursOperationCount,
        int theirsOperationCount,
        int safeOperationCount,
        int conflictOperationCount,
        int unsupportedOperationCount,
        int blockedOperationCount,
        int skippedDuplicateCount,
        int conflictCount,
        boolean manualReviewRequired,
        boolean canAutoApplyInFuture,
        Map<String, String> safeMetadata) {

    public TimelineMergePlanSummary {
        if (safeMetadata == null) safeMetadata = Map.of();
    }

    public static TimelineMergePlanSummary of(
            int oursOps, int theirsOps,
            int safeOps, int conflictOps, int unsupportedOps,
            int blockedOps, int skippedDups,
            int conflictCount, boolean manualReview, boolean canAutoApply) {
        return new TimelineMergePlanSummary(
                oursOps, theirsOps, safeOps, conflictOps, unsupportedOps,
                blockedOps, skippedDups, conflictCount, manualReview, canAutoApply, Map.of());
    }
}
