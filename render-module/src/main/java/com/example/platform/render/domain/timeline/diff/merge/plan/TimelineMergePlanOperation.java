package com.example.platform.render.domain.timeline.diff.merge.plan;

import com.example.platform.render.domain.timeline.diff.TimelineChangeOperation;
import com.example.platform.render.domain.timeline.diff.TimelineConflict;
import java.util.List;
import java.util.Map;

/**
 * A single classified operation within a merge plan.
 * Internal domain model. Provider-neutral, storage-neutral.
 */
public record TimelineMergePlanOperation(
        TimelineMergePlanOperationStatus status,
        TimelineMergePlanOperationSource source,
        TimelineChangeOperation operation,
        String path,
        List<TimelineConflict> relatedConflicts,
        List<TimelineMergePlanIssue> issues,
        Map<String, String> safeMetadata) {

    public TimelineMergePlanOperation {
        if (status == null) throw new IllegalArgumentException("Operation status must not be null");
        if (source == null) throw new IllegalArgumentException("Operation source must not be null");
        if (relatedConflicts == null) relatedConflicts = List.of();
        if (issues == null) issues = List.of();
        if (safeMetadata == null) safeMetadata = Map.of();
    }

    /**
     * Create a safe operation from ours.
     */
    public static TimelineMergePlanOperation safeFromOurs(TimelineChangeOperation operation) {
        return new TimelineMergePlanOperation(
                TimelineMergePlanOperationStatus.SAFE_TO_APPLY_LATER,
                TimelineMergePlanOperationSource.OURS,
                operation,
                operation != null && operation.path() != null ? operation.path().value() : null,
                List.of(), List.of(), Map.of());
    }

    /**
     * Create a safe operation from theirs.
     */
    public static TimelineMergePlanOperation safeFromTheirs(TimelineChangeOperation operation) {
        return new TimelineMergePlanOperation(
                TimelineMergePlanOperationStatus.SAFE_TO_APPLY_LATER,
                TimelineMergePlanOperationSource.THEIRS,
                operation,
                operation != null && operation.path() != null ? operation.path().value() : null,
                List.of(), List.of(), Map.of());
    }

    /**
     * Create a conflicting operation.
     */
    public static TimelineMergePlanOperation conflict(
            TimelineMergePlanOperationSource source,
            TimelineChangeOperation operation,
            List<TimelineConflict> relatedConflicts,
            List<TimelineMergePlanIssue> issues) {
        return new TimelineMergePlanOperation(
                TimelineMergePlanOperationStatus.CONFLICT_REQUIRES_MANUAL_REVIEW,
                source,
                operation,
                operation != null && operation.path() != null ? operation.path().value() : null,
                relatedConflicts != null ? relatedConflicts : List.of(),
                issues != null ? issues : List.of(),
                Map.of());
    }

    /**
     * Create a skipped-duplicate operation.
     */
    public static TimelineMergePlanOperation skippedDuplicate(
            TimelineChangeOperation operation,
            TimelineMergePlanOperationSource source) {
        return new TimelineMergePlanOperation(
                TimelineMergePlanOperationStatus.SKIPPED_DUPLICATE,
                source,
                operation,
                operation != null && operation.path() != null ? operation.path().value() : null,
                List.of(), List.of(), Map.of());
    }

    /**
     * Create a blocked operation.
     */
    public static TimelineMergePlanOperation blocked(
            TimelineChangeOperation operation,
            TimelineMergePlanIssue issue) {
        return new TimelineMergePlanOperation(
                TimelineMergePlanOperationStatus.BLOCKED,
                TimelineMergePlanOperationSource.SYSTEM,
                operation,
                operation != null && operation.path() != null ? operation.path().value() : null,
                List.of(), List.of(issue), Map.of());
    }

    /**
     * Create an unsupported operation.
     */
    public static TimelineMergePlanOperation unsupported(
            TimelineChangeOperation operation,
            TimelineMergePlanIssue issue) {
        return new TimelineMergePlanOperation(
                TimelineMergePlanOperationStatus.UNSUPPORTED,
                TimelineMergePlanOperationSource.SYSTEM,
                operation,
                operation != null && operation.path() != null ? operation.path().value() : null,
                List.of(), List.of(issue), Map.of());
    }
}
