package com.example.platform.render.domain.timeline.version.application;

import com.example.platform.render.domain.timeline.diff.calculation.CanonicalTimelineSnapshot;
import com.example.platform.render.domain.timeline.version.TimelineBranch;
import com.example.platform.render.domain.timeline.version.TimelineBranchName;
import com.example.platform.render.domain.timeline.version.TimelineBranchSwitchPlan;
import java.util.List;
import java.util.Map;

/**
 * Result of a timeline branch switch.
 * Internal domain model. Does not mutate branch pointer, render, or create Product.
 */
public record TimelineBranchSwitchResult(
        TimelineBranchSwitchResultStatus status,
        TimelineBranchSwitchPlan switchPlan,
        TimelineBranch targetBranch,
        CanonicalTimelineSnapshot targetSnapshot,
        List<TimelineVersionApplicationIssue> issues,
        Map<String, String> safeMetadata
) {
    public TimelineBranchSwitchResult {
        if (status == null) throw new IllegalArgumentException("Status must not be null");
        if (issues == null) issues = List.of();
        else issues = List.copyOf(issues);
        if (safeMetadata == null) safeMetadata = Map.of();
        else safeMetadata = Map.copyOf(safeMetadata);
    }

    public static TimelineBranchSwitchResult ready(
            TimelineBranchSwitchPlan plan,
            TimelineBranch targetBranch,
            CanonicalTimelineSnapshot targetSnapshot) {
        return new TimelineBranchSwitchResult(
                TimelineBranchSwitchResultStatus.READY, plan, targetBranch, targetSnapshot,
                List.of(), Map.of());
    }

    public static TimelineBranchSwitchResult sourceBranchNotFound(
            List<TimelineVersionApplicationIssue> issues) {
        return new TimelineBranchSwitchResult(
                TimelineBranchSwitchResultStatus.SOURCE_BRANCH_NOT_FOUND, null, null, null,
                issues, Map.of());
    }

    public static TimelineBranchSwitchResult targetBranchNotFound(
            List<TimelineVersionApplicationIssue> issues) {
        return new TimelineBranchSwitchResult(
                TimelineBranchSwitchResultStatus.TARGET_BRANCH_NOT_FOUND, null, null, null,
                issues, Map.of());
    }

    public static TimelineBranchSwitchResult unsavedChangesRequireDecision(
            TimelineBranchName sourceBranch,
            TimelineBranchName targetBranch,
            List<TimelineVersionApplicationIssue> issues) {
        var plan = TimelineBranchSwitchPlan.unsavedChangesRequireDecision(
                sourceBranch, targetBranch, List.of());
        return new TimelineBranchSwitchResult(
                TimelineBranchSwitchResultStatus.UNSAVED_CHANGES_REQUIRE_DECISION,
                plan, null, null, issues, Map.of());
    }

    public static TimelineBranchSwitchResult invalidRequest(
            List<TimelineVersionApplicationIssue> issues) {
        return new TimelineBranchSwitchResult(
                TimelineBranchSwitchResultStatus.INVALID_REQUEST, null, null, null,
                issues, Map.of());
    }

    public static TimelineBranchSwitchResult blocked(
            List<TimelineVersionApplicationIssue> issues) {
        return new TimelineBranchSwitchResult(
                TimelineBranchSwitchResultStatus.BLOCKED, null, null, null,
                issues, Map.of());
    }

    public static TimelineBranchSwitchResult failed(
            List<TimelineVersionApplicationIssue> issues) {
        return new TimelineBranchSwitchResult(
                TimelineBranchSwitchResultStatus.FAILED, null, null, null,
                issues, Map.of());
    }
}
