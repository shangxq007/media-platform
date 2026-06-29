package com.example.platform.render.domain.timeline.version.application;

import com.example.platform.render.domain.timeline.diff.calculation.CanonicalTimelineSnapshot;
import com.example.platform.render.domain.timeline.version.TimelineBranch;
import com.example.platform.render.domain.timeline.version.TimelineBranchSemanticsPlanner;
import com.example.platform.render.domain.timeline.version.TimelineCheckoutPlan;
import com.example.platform.render.domain.timeline.version.TimelineCommit;
import java.util.List;
import java.util.Map;

/**
 * Pure, side-effect-free timeline checkout service.
 * Not API. Not persistence. Not render.
 * Does not create Product, call StorageRuntime, or call ProductRuntime.
 */
public class TimelineCheckoutService {

    private final TimelineVersionLookup lookup;
    private final TimelineBranchSemanticsPlanner planner;

    public TimelineCheckoutService(
            TimelineVersionLookup lookup,
            TimelineBranchSemanticsPlanner planner) {
        if (lookup == null) throw new IllegalArgumentException("Lookup must not be null");
        if (planner == null) throw new IllegalArgumentException("Planner must not be null");
        this.lookup = lookup;
        this.planner = planner;
    }

    public TimelineCheckoutResult checkout(TimelineCheckoutRequest request) {
        if (request == null) {
            return TimelineCheckoutResult.invalidTarget(null,
                    List.of(issue(TimelineVersionApplicationIssueSeverity.BLOCKING,
                            TimelineVersionApplicationIssueCode.INVALID_REQUEST,
                            "Request must not be null")));
        }

        var target = request.target();
        if (target == null) {
            return TimelineCheckoutResult.invalidTarget(null,
                    List.of(issue(TimelineVersionApplicationIssueSeverity.BLOCKING,
                            TimelineVersionApplicationIssueCode.INVALID_TARGET,
                            "Target must not be null")));
        }

        return switch (target.type()) {
            case BRANCH -> checkoutByBranch(target);
            case REVISION -> checkoutByRevision(target);
            case COMMIT -> checkoutByCommit(target);
        };
    }

    private TimelineCheckoutResult checkoutByBranch(TimelineCheckoutTarget target) {
        var branchName = target.branchName();
        if (branchName == null) {
            return TimelineCheckoutResult.invalidTarget(target,
                    List.of(issue(TimelineVersionApplicationIssueSeverity.ERROR,
                            TimelineVersionApplicationIssueCode.INVALID_TARGET,
                            "Branch name required for BRANCH target")));
        }

        var branch = lookup.findBranch(branchName);
        if (branch.isEmpty()) {
            return TimelineCheckoutResult.branchNotFound(target,
                    List.of(issue(TimelineVersionApplicationIssueSeverity.ERROR,
                            TimelineVersionApplicationIssueCode.BRANCH_NOT_FOUND,
                            "Branch not found: " + branchName.value())));
        }

        TimelineBranch b = branch.get();
        TimelineCheckoutPlan plan = planner.planCheckout(b);
        var snapshot = lookup.findSnapshot(b.headRevision());
        return TimelineCheckoutResult.ready(plan, target, snapshot.orElse(null));
    }

    private TimelineCheckoutResult checkoutByRevision(TimelineCheckoutTarget target) {
        var revisionRef = target.revisionRef();
        if (revisionRef == null) {
            return TimelineCheckoutResult.invalidTarget(target,
                    List.of(issue(TimelineVersionApplicationIssueSeverity.ERROR,
                            TimelineVersionApplicationIssueCode.INVALID_TARGET,
                            "Revision ref required for REVISION target")));
        }

        var snapshot = lookup.findSnapshot(revisionRef);
        if (snapshot.isEmpty()) {
            return TimelineCheckoutResult.revisionNotFound(target,
                    List.of(issue(TimelineVersionApplicationIssueSeverity.ERROR,
                            TimelineVersionApplicationIssueCode.REVISION_NOT_FOUND,
                            "Revision not found: " + revisionRef.value())));
        }

        TimelineCheckoutPlan plan = planner.planCheckout(revisionRef);
        return TimelineCheckoutResult.ready(plan, target, snapshot.get());
    }

    private TimelineCheckoutResult checkoutByCommit(TimelineCheckoutTarget target) {
        var commitId = target.commitId();
        if (commitId == null) {
            return TimelineCheckoutResult.invalidTarget(target,
                    List.of(issue(TimelineVersionApplicationIssueSeverity.ERROR,
                            TimelineVersionApplicationIssueCode.INVALID_TARGET,
                            "Commit id required for COMMIT target")));
        }

        var commit = lookup.findCommit(commitId);
        if (commit.isEmpty()) {
            return TimelineCheckoutResult.commitNotFound(target,
                    List.of(issue(TimelineVersionApplicationIssueSeverity.ERROR,
                            TimelineVersionApplicationIssueCode.COMMIT_NOT_FOUND,
                            "Commit not found: " + commitId.value())));
        }

        TimelineCommit c = commit.get();
        var snapshot = lookup.findSnapshot(c.revisionRef());
        TimelineCheckoutPlan plan = planner.planCheckout(c.revisionRef());
        return TimelineCheckoutResult.ready(plan, target, snapshot.orElse(null));
    }

    private TimelineVersionApplicationIssue issue(
            TimelineVersionApplicationIssueSeverity severity,
            TimelineVersionApplicationIssueCode code,
            String message) {
        return new TimelineVersionApplicationIssue(severity, code, message, Map.of());
    }
}
