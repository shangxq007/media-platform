package com.example.platform.render.domain.timeline.version;

import java.util.List;
import java.util.Map;

/**
 * Plan for switching editing context from one branch to another.
 * Internal domain model.
 *
 * <p>Switch changes editing context only.
 * Does not render, create Product, or mutate branch pointer.</p>
 */
public record TimelineBranchSwitchPlan(
        TimelineBranchSwitchStatus status,
        TimelineBranchName sourceBranch,
        TimelineBranchName targetBranch,
        TimelineRevisionRef targetRevision,
        List<TimelineBranchOperationIssue> issues,
        Map<String, String> safeMetadata) {

    public TimelineBranchSwitchPlan {
        if (status == null) throw new IllegalArgumentException("Status must not be null");
        if (issues == null) issues = List.of();
        else issues = List.copyOf(issues);
        if (safeMetadata == null) safeMetadata = Map.of();
        else safeMetadata = Map.copyOf(safeMetadata);
    }

    public static TimelineBranchSwitchPlan ready(
            TimelineBranchName source, TimelineBranchName target, TimelineRevisionRef targetRevision) {
        return new TimelineBranchSwitchPlan(
                TimelineBranchSwitchStatus.READY, source, target, targetRevision, List.of(), Map.of());
    }

    public static TimelineBranchSwitchPlan sourceBranchNotFound(List<TimelineBranchOperationIssue> issues) {
        return new TimelineBranchSwitchPlan(
                TimelineBranchSwitchStatus.SOURCE_BRANCH_NOT_FOUND, null, null, null, issues, Map.of());
    }

    public static TimelineBranchSwitchPlan targetBranchNotFound(List<TimelineBranchOperationIssue> issues) {
        return new TimelineBranchSwitchPlan(
                TimelineBranchSwitchStatus.TARGET_BRANCH_NOT_FOUND, null, null, null, issues, Map.of());
    }

    public static TimelineBranchSwitchPlan unsavedChangesRequireDecision(
            TimelineBranchName source, TimelineBranchName target,
            List<TimelineBranchOperationIssue> issues) {
        return new TimelineBranchSwitchPlan(
                TimelineBranchSwitchStatus.UNSAVED_CHANGES_REQUIRE_DECISION, source, target, null, issues, Map.of());
    }

    public static TimelineBranchSwitchPlan blocked(List<TimelineBranchOperationIssue> issues) {
        return new TimelineBranchSwitchPlan(
                TimelineBranchSwitchStatus.BLOCKED, null, null, null, issues, Map.of());
    }
}
