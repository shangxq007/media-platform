package com.example.platform.render.domain.timeline.version.application;

import com.example.platform.render.domain.timeline.diff.calculation.CanonicalTimelineSnapshot;
import com.example.platform.render.domain.timeline.version.TimelineCheckoutPlan;
import java.util.List;
import java.util.Map;

/**
 * Result of a timeline checkout.
 * Internal domain model. No render, no Product creation, no storage materialization.
 */
public record TimelineCheckoutResult(
        TimelineCheckoutResultStatus status,
        TimelineCheckoutPlan checkoutPlan,
        TimelineCheckoutTarget target,
        CanonicalTimelineSnapshot snapshot,
        List<TimelineVersionApplicationIssue> issues,
        Map<String, String> safeMetadata
) {
    public TimelineCheckoutResult {
        if (status == null) throw new IllegalArgumentException("Status must not be null");
        if (issues == null) issues = List.of();
        else issues = List.copyOf(issues);
        if (safeMetadata == null) safeMetadata = Map.of();
        else safeMetadata = Map.copyOf(safeMetadata);
    }

    public static TimelineCheckoutResult ready(
            TimelineCheckoutPlan plan,
            TimelineCheckoutTarget target,
            CanonicalTimelineSnapshot snapshot) {
        return new TimelineCheckoutResult(
                TimelineCheckoutResultStatus.READY, plan, target, snapshot, List.of(), Map.of());
    }

    public static TimelineCheckoutResult branchNotFound(
            TimelineCheckoutTarget target,
            List<TimelineVersionApplicationIssue> issues) {
        return new TimelineCheckoutResult(
                TimelineCheckoutResultStatus.BRANCH_NOT_FOUND, null, target, null, issues, Map.of());
    }

    public static TimelineCheckoutResult revisionNotFound(
            TimelineCheckoutTarget target,
            List<TimelineVersionApplicationIssue> issues) {
        return new TimelineCheckoutResult(
                TimelineCheckoutResultStatus.REVISION_NOT_FOUND, null, target, null, issues, Map.of());
    }

    public static TimelineCheckoutResult commitNotFound(
            TimelineCheckoutTarget target,
            List<TimelineVersionApplicationIssue> issues) {
        return new TimelineCheckoutResult(
                TimelineCheckoutResultStatus.COMMIT_NOT_FOUND, null, target, null, issues, Map.of());
    }

    public static TimelineCheckoutResult invalidTarget(
            TimelineCheckoutTarget target,
            List<TimelineVersionApplicationIssue> issues) {
        return new TimelineCheckoutResult(
                TimelineCheckoutResultStatus.INVALID_TARGET, null, target, null, issues, Map.of());
    }

    public static TimelineCheckoutResult blocked(
            TimelineCheckoutTarget target,
            List<TimelineVersionApplicationIssue> issues) {
        return new TimelineCheckoutResult(
                TimelineCheckoutResultStatus.BLOCKED, null, target, null, issues, Map.of());
    }

    public static TimelineCheckoutResult failed(
            TimelineCheckoutTarget target,
            List<TimelineVersionApplicationIssue> issues) {
        return new TimelineCheckoutResult(
                TimelineCheckoutResultStatus.FAILED, null, target, null, issues, Map.of());
    }
}
