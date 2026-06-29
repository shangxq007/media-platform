package com.example.platform.render.domain.timeline.diff.merge;

import java.util.List;
import java.util.Map;

/**
 * Merge readiness assessment. Internal domain model.
 * Provider-neutral, storage-neutral.
 */
public record TimelineMergeReadiness(
        TimelineMergeReadinessStatus status,
        boolean mergeReady,
        boolean manualReviewRequired,
        List<TimelineMergeConflictIssue> issues,
        Map<String, String> safeMetadata) {

    public static TimelineMergeReadiness ready() {
        return new TimelineMergeReadiness(
                TimelineMergeReadinessStatus.MERGE_READY, true, false, List.of(), Map.of());
    }

    public static TimelineMergeReadiness manualReview(List<TimelineMergeConflictIssue> issues) {
        return new TimelineMergeReadiness(
                TimelineMergeReadinessStatus.MANUAL_REVIEW_REQUIRED, false, true, issues, Map.of());
    }

    public static TimelineMergeReadiness blocked(List<TimelineMergeConflictIssue> issues) {
        return new TimelineMergeReadiness(
                TimelineMergeReadinessStatus.BLOCKED, false, false, issues, Map.of());
    }

    public static TimelineMergeReadiness unsupported(List<TimelineMergeConflictIssue> issues) {
        return new TimelineMergeReadiness(
                TimelineMergeReadinessStatus.UNSUPPORTED, false, false, issues, Map.of());
    }

    public static TimelineMergeReadiness invalidInput(List<TimelineMergeConflictIssue> issues) {
        return new TimelineMergeReadiness(
                TimelineMergeReadinessStatus.INVALID_INPUT, false, false, issues, Map.of());
    }
}
