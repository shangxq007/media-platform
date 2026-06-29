package com.example.platform.render.domain.timeline.diff.merge.plan;

import com.example.platform.render.domain.timeline.diff.merge.preview.TimelineMergePreviewResult;
import java.util.List;
import java.util.Map;

/**
 * Result of non-conflicting merge plan generation.
 * Internal domain model. Does not merge, apply patches, or persist.
 * Provider-neutral, storage-neutral.
 */
public record TimelineNonConflictingMergePlan(
        TimelineMergePlanId id,
        TimelineMergePlanStatus status,
        TimelineMergePreviewResult previewResult,
        List<TimelineMergePlanOperation> operations,
        TimelineMergePlanSummary summary,
        List<TimelineMergePlanIssue> issues,
        Map<String, String> safeMetadata) {

    public TimelineNonConflictingMergePlan {
        if (id == null) throw new IllegalArgumentException("Plan ID must not be null");
        if (status == null) throw new IllegalArgumentException("Status must not be null");
        if (operations == null) operations = List.of();
        if (issues == null) issues = List.of();
        if (safeMetadata == null) safeMetadata = Map.of();
    }

    public boolean isReady() {
        return status == TimelineMergePlanStatus.READY;
    }

    public boolean hasConflicts() {
        return operations.stream().anyMatch(
                op -> op.status() == TimelineMergePlanOperationStatus.CONFLICT_REQUIRES_MANUAL_REVIEW);
    }

    /**
     * Factory: READY plan with all safe operations.
     */
    public static TimelineNonConflictingMergePlan ready(
            TimelineMergePlanId id,
            TimelineMergePreviewResult previewResult,
            List<TimelineMergePlanOperation> operations,
            TimelineMergePlanSummary summary) {
        return new TimelineNonConflictingMergePlan(
                id, TimelineMergePlanStatus.READY, previewResult, operations, summary, List.of(), Map.of());
    }

    /**
     * Factory: MANUAL_REVIEW_REQUIRED plan with mixed operations.
     */
    public static TimelineNonConflictingMergePlan manualReview(
            TimelineMergePlanId id,
            TimelineMergePreviewResult previewResult,
            List<TimelineMergePlanOperation> operations,
            TimelineMergePlanSummary summary,
            List<TimelineMergePlanIssue> issues) {
        return new TimelineNonConflictingMergePlan(
                id, TimelineMergePlanStatus.MANUAL_REVIEW_REQUIRED, previewResult,
                operations, summary, issues, Map.of());
    }

    /**
     * Factory: BLOCKED plan.
     */
    public static TimelineNonConflictingMergePlan blocked(
            TimelineMergePlanId id,
            List<TimelineMergePlanIssue> issues) {
        return new TimelineNonConflictingMergePlan(
                id, TimelineMergePlanStatus.BLOCKED, null, List.of(), null, issues, Map.of());
    }

    /**
     * Factory: INVALID_INPUT plan.
     */
    public static TimelineNonConflictingMergePlan invalidInput(
            TimelineMergePlanId id,
            List<TimelineMergePlanIssue> issues) {
        return new TimelineNonConflictingMergePlan(
                id, TimelineMergePlanStatus.INVALID_INPUT, null, List.of(), null, issues, Map.of());
    }

    /**
     * Factory: UNSUPPORTED plan.
     */
    public static TimelineNonConflictingMergePlan unsupported(
            TimelineMergePlanId id,
            List<TimelineMergePlanIssue> issues) {
        return new TimelineNonConflictingMergePlan(
                id, TimelineMergePlanStatus.UNSUPPORTED, null, List.of(), null, issues, Map.of());
    }

    /**
     * Factory: FAILED plan.
     */
    public static TimelineNonConflictingMergePlan failed(
            TimelineMergePlanId id,
            List<TimelineMergePlanIssue> issues) {
        return new TimelineNonConflictingMergePlan(
                id, TimelineMergePlanStatus.FAILED, null, List.of(), null, issues, Map.of());
    }
}
