package com.example.platform.render.domain.timeline.version.application;

import com.example.platform.render.domain.timeline.version.TimelineRevisionRef;
import com.example.platform.render.domain.timeline.version.TimelineRollbackPlan;
import java.util.List;
import java.util.Map;

/**
 * Result of a timeline rollback planning.
 * Internal domain model. Non-destructive. Does not persist, render, or create Product.
 */
public record TimelineRollbackResult(
        TimelineRollbackResultStatus status,
        TimelineRollbackPlan rollbackPlan,
        TimelineRollbackIntent intent,
        List<TimelineVersionApplicationIssue> issues,
        Map<String, String> safeMetadata
) {
    public TimelineRollbackResult {
        if (status == null) throw new IllegalArgumentException("Status must not be null");
        if (issues == null) issues = List.of();
        else issues = List.copyOf(issues);
        if (safeMetadata == null) safeMetadata = Map.of();
        else safeMetadata = Map.copyOf(safeMetadata);
    }

    public static TimelineRollbackResult ready(
            TimelineRollbackPlan plan, TimelineRollbackIntent intent) {
        return new TimelineRollbackResult(
                TimelineRollbackResultStatus.READY, plan, intent, List.of(), Map.of());
    }

    public static TimelineRollbackResult noOp(
            TimelineRollbackPlan plan,
            TimelineRevisionRef currentRevision,
            TimelineRevisionRef targetRevision) {
        var intent = new TimelineRollbackIntent(
                null, currentRevision, targetRevision, "No-op: current equals target", Map.of());
        return new TimelineRollbackResult(
                TimelineRollbackResultStatus.NO_OP, plan, intent, List.of(), Map.of());
    }

    public static TimelineRollbackResult targetNotFound(
            List<TimelineVersionApplicationIssue> issues) {
        return new TimelineRollbackResult(
                TimelineRollbackResultStatus.TARGET_NOT_FOUND, null, null, issues, Map.of());
    }

    public static TimelineRollbackResult targetNotAncestor(
            List<TimelineVersionApplicationIssue> issues) {
        return new TimelineRollbackResult(
                TimelineRollbackResultStatus.TARGET_NOT_ANCESTOR, null, null, issues, Map.of());
    }

    public static TimelineRollbackResult invalidRequest(
            List<TimelineVersionApplicationIssue> issues) {
        return new TimelineRollbackResult(
                TimelineRollbackResultStatus.INVALID_REQUEST, null, null, issues, Map.of());
    }

    public static TimelineRollbackResult blocked(
            List<TimelineVersionApplicationIssue> issues) {
        return new TimelineRollbackResult(
                TimelineRollbackResultStatus.BLOCKED, null, null, issues, Map.of());
    }

    public static TimelineRollbackResult failed(
            List<TimelineVersionApplicationIssue> issues) {
        return new TimelineRollbackResult(
                TimelineRollbackResultStatus.FAILED, null, null, issues, Map.of());
    }
}
