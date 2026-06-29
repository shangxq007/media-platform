package com.example.platform.render.domain.timeline.version.application;

import com.example.platform.render.domain.timeline.diff.calculation.CanonicalTimelineSnapshot;
import com.example.platform.render.domain.timeline.version.TimelineBranch;
import com.example.platform.render.domain.timeline.version.TimelineBranchSemanticsPlanner;
import java.util.List;
import java.util.Map;

/**
 * Pure, side-effect-free timeline branch switch service.
 * Not pointer mutation. Not persistence.
 * Does not render, create Product, or stash.
 */
public class TimelineBranchSwitchService {

    private final TimelineVersionLookup lookup;
    private final TimelineBranchSemanticsPlanner planner;

    public TimelineBranchSwitchService(
            TimelineVersionLookup lookup,
            TimelineBranchSemanticsPlanner planner) {
        if (lookup == null) throw new IllegalArgumentException("Lookup must not be null");
        if (planner == null) throw new IllegalArgumentException("Planner must not be null");
        this.lookup = lookup;
        this.planner = planner;
    }

    public TimelineBranchSwitchResult planSwitch(TimelineBranchSwitchRequest request) {
        if (request == null) {
            return TimelineBranchSwitchResult.invalidRequest(
                    List.of(issue(TimelineVersionApplicationIssueSeverity.BLOCKING,
                            TimelineVersionApplicationIssueCode.INVALID_REQUEST,
                            "Request must not be null")));
        }

        var sourceName = request.sourceBranch();
        var targetName = request.targetBranch();

        // Find source branch
        var sourceBranch = lookup.findBranch(sourceName);
        if (sourceBranch.isEmpty()) {
            return TimelineBranchSwitchResult.sourceBranchNotFound(
                    List.of(issue(TimelineVersionApplicationIssueSeverity.ERROR,
                            TimelineVersionApplicationIssueCode.SOURCE_BRANCH_NOT_FOUND,
                            "Source branch not found: " + sourceName.value())));
        }

        // Find target branch
        var targetBranch = lookup.findBranch(targetName);
        if (targetBranch.isEmpty()) {
            return TimelineBranchSwitchResult.targetBranchNotFound(
                    List.of(issue(TimelineVersionApplicationIssueSeverity.ERROR,
                            TimelineVersionApplicationIssueCode.TARGET_BRANCH_NOT_FOUND,
                            "Target branch not found: " + targetName.value())));
        }

        // Unsaved changes check
        if (request.hasUnsavedChanges()) {
            return TimelineBranchSwitchResult.unsavedChangesRequireDecision(
                    sourceName, targetName,
                    List.of(issue(TimelineVersionApplicationIssueSeverity.WARNING,
                            TimelineVersionApplicationIssueCode.UNSAVED_CHANGES_REQUIRE_DECISION,
                            "Unsaved changes require explicit decision before switch")));
        }

        // Build switch plan
        TimelineBranch source = sourceBranch.get();
        TimelineBranch target = targetBranch.get();
        var plan = planner.planSwitch(source, target, false);

        // Resolve target snapshot if available
        CanonicalTimelineSnapshot snapshot = lookup.findSnapshot(target.headRevision()).orElse(null);

        return TimelineBranchSwitchResult.ready(plan, target, snapshot);
    }

    private TimelineVersionApplicationIssue issue(
            TimelineVersionApplicationIssueSeverity severity,
            TimelineVersionApplicationIssueCode code,
            String message) {
        return new TimelineVersionApplicationIssue(severity, code, message, Map.of());
    }
}
