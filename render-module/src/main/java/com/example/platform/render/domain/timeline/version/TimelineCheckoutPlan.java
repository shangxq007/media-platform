package com.example.platform.render.domain.timeline.version;

import java.util.List;
import java.util.Map;

/**
 * Plan for reading/editing context for a branch or revision target.
 * Internal domain model. Does not render, create Product, persist, or modify branch pointer.
 */
public record TimelineCheckoutPlan(
        TimelineCheckoutStatus status,
        TimelineBranchName branchName,
        TimelineRevisionRef targetRevision,
        List<TimelineBranchOperationIssue> issues,
        Map<String, String> safeMetadata) {

    public TimelineCheckoutPlan {
        if (status == null) throw new IllegalArgumentException("Status must not be null");
        if (issues == null) issues = List.of();
        else issues = List.copyOf(issues);
        if (safeMetadata == null) safeMetadata = Map.of();
        else safeMetadata = Map.copyOf(safeMetadata);
    }

    public static TimelineCheckoutPlan ready(TimelineBranchName branchName, TimelineRevisionRef targetRevision) {
        return new TimelineCheckoutPlan(
                TimelineCheckoutStatus.READY, branchName, targetRevision, List.of(), Map.of());
    }

    public static TimelineCheckoutPlan invalidTarget(List<TimelineBranchOperationIssue> issues) {
        return new TimelineCheckoutPlan(
                TimelineCheckoutStatus.INVALID_TARGET, null, null, issues, Map.of());
    }

    public static TimelineCheckoutPlan branchNotFound(List<TimelineBranchOperationIssue> issues) {
        return new TimelineCheckoutPlan(
                TimelineCheckoutStatus.BRANCH_NOT_FOUND, null, null, issues, Map.of());
    }

    public static TimelineCheckoutPlan revisionNotFound(List<TimelineBranchOperationIssue> issues) {
        return new TimelineCheckoutPlan(
                TimelineCheckoutStatus.REVISION_NOT_FOUND, null, null, issues, Map.of());
    }

    public static TimelineCheckoutPlan blocked(List<TimelineBranchOperationIssue> issues) {
        return new TimelineCheckoutPlan(
                TimelineCheckoutStatus.BLOCKED, null, null, issues, Map.of());
    }
}
