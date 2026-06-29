package com.example.platform.render.domain.timeline.version;

import java.util.List;
import java.util.Map;

/**
 * Plan for non-destructive rollback to a previous revision.
 * Internal domain model.
 *
 * <p>Rollback creates a new revision/commit semantics.
 * Does not delete history, hard reset, apply persistence, or render.</p>
 */
public record TimelineRollbackPlan(
        TimelineRollbackStatus status,
        TimelineRevisionRef currentRevision,
        TimelineRevisionRef targetRevision,
        TimelineCommitType plannedCommitType,
        List<TimelineBranchOperationIssue> issues,
        Map<String, String> safeMetadata) {

    public TimelineRollbackPlan {
        if (status == null) throw new IllegalArgumentException("Status must not be null");
        if (plannedCommitType == null) plannedCommitType = TimelineCommitType.ROLLBACK;
        if (issues == null) issues = List.of();
        else issues = List.copyOf(issues);
        if (safeMetadata == null) safeMetadata = Map.of();
        else safeMetadata = Map.copyOf(safeMetadata);
    }

    public static TimelineRollbackPlan ready(
            TimelineRevisionRef current, TimelineRevisionRef target) {
        return new TimelineRollbackPlan(
                TimelineRollbackStatus.READY, current, target,
                TimelineCommitType.ROLLBACK, List.of(), Map.of());
    }

    public static TimelineRollbackPlan noOp(
            TimelineRevisionRef current, TimelineRevisionRef target) {
        return new TimelineRollbackPlan(
                TimelineRollbackStatus.NO_OP, current, target,
                TimelineCommitType.ROLLBACK, List.of(), Map.of());
    }

    public static TimelineRollbackPlan invalidTarget(List<TimelineBranchOperationIssue> issues) {
        return new TimelineRollbackPlan(
                TimelineRollbackStatus.INVALID_TARGET, null, null,
                TimelineCommitType.ROLLBACK, issues, Map.of());
    }

    public static TimelineRollbackPlan targetNotAncestor(
            TimelineRevisionRef current, TimelineRevisionRef target,
            List<TimelineBranchOperationIssue> issues) {
        return new TimelineRollbackPlan(
                TimelineRollbackStatus.TARGET_NOT_ANCESTOR, current, target,
                TimelineCommitType.ROLLBACK, issues, Map.of());
    }

    public static TimelineRollbackPlan blocked(List<TimelineBranchOperationIssue> issues) {
        return new TimelineRollbackPlan(
                TimelineRollbackStatus.BLOCKED, null, null,
                TimelineCommitType.ROLLBACK, issues, Map.of());
    }
}
