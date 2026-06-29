package com.example.platform.render.domain.timeline.version.application;

import com.example.platform.render.domain.timeline.version.TimelineBranchSemanticsPlanner;
import com.example.platform.render.domain.timeline.version.TimelineCommitType;
import com.example.platform.render.domain.timeline.version.TimelineRollbackPlan;
import java.util.List;
import java.util.Map;

/**
 * Pure, side-effect-free timeline rollback service.
 * Not patch application. Not history rewrite.
 * Does not persist, render, or create Product.
 */
public class TimelineRollbackService {

    private final TimelineVersionLookup lookup;
    private final TimelineBranchSemanticsPlanner planner;

    public TimelineRollbackService(
            TimelineVersionLookup lookup,
            TimelineBranchSemanticsPlanner planner) {
        if (lookup == null) throw new IllegalArgumentException("Lookup must not be null");
        if (planner == null) throw new IllegalArgumentException("Planner must not be null");
        this.lookup = lookup;
        this.planner = planner;
    }

    public TimelineRollbackResult planRollback(TimelineRollbackRequest request) {
        if (request == null) {
            return TimelineRollbackResult.invalidRequest(
                    List.of(issue(TimelineVersionApplicationIssueSeverity.BLOCKING,
                            TimelineVersionApplicationIssueCode.INVALID_REQUEST,
                            "Request must not be null")));
        }

        var current = request.currentRevision();
        var target = request.targetRevision();

        // Check if target snapshot exists
        var targetSnapshot = lookup.findSnapshot(target);
        if (targetSnapshot.isEmpty()) {
            return TimelineRollbackResult.targetNotFound(
                    List.of(issue(TimelineVersionApplicationIssueSeverity.ERROR,
                            TimelineVersionApplicationIssueCode.REVISION_NOT_FOUND,
                            "Target revision not found: " + target.value())));
        }

        // Same revision = NO_OP
        if (current.value().equals(target.value())) {
            TimelineRollbackPlan plan = planner.planRollback(current, target);
            return TimelineRollbackResult.noOp(plan, current, target);
        }

        // Ancestry check: if required but no graph available, return TARGET_NOT_ANCESTOR
        if (request.requireTargetAncestor()) {
            // No commit graph/ancestry lookup available yet
            return TimelineRollbackResult.targetNotAncestor(
                    List.of(issue(TimelineVersionApplicationIssueSeverity.WARNING,
                            TimelineVersionApplicationIssueCode.TARGET_NOT_ANCESTOR,
                            "Ancestry verification not available; TARGET_NOT_ANCESTOR returned conservatively")));
        }

        // Build rollback plan
        TimelineRollbackPlan plan = planner.planRollback(current, target);

        // Build rollback intent
        var intent = new TimelineRollbackIntent(
                TimelineCommitType.ROLLBACK,
                current,
                target,
                "Rollback from " + current.value() + " to " + target.value(),
                Map.of());

        return TimelineRollbackResult.ready(plan, intent);
    }

    private TimelineVersionApplicationIssue issue(
            TimelineVersionApplicationIssueSeverity severity,
            TimelineVersionApplicationIssueCode code,
            String message) {
        return new TimelineVersionApplicationIssue(severity, code, message, Map.of());
    }
}
