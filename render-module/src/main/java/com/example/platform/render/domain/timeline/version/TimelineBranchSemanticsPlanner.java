package com.example.platform.render.domain.timeline.version;

import java.util.List;
import java.util.Map;

/**
 * Pure, side-effect-free planner for timeline branch semantics.
 * Internal domain model. No persistence, no render, no Product creation.
 */
public class TimelineBranchSemanticsPlanner {

    /**
     * Plan checkout for a branch.
     */
    public TimelineCheckoutPlan planCheckout(TimelineBranch branch) {
        if (branch == null) {
            return TimelineCheckoutPlan.invalidTarget(List.of(
                    new TimelineBranchOperationIssue(
                            TimelineBranchOperationIssueSeverity.ERROR,
                            TimelineBranchOperationIssueCode.INVALID_BRANCH_ID,
                            "Branch must not be null", Map.of())));
        }
        return TimelineCheckoutPlan.ready(branch.name(), branch.headRevision());
    }

    /**
     * Plan checkout for a specific revision.
     */
    public TimelineCheckoutPlan planCheckout(TimelineRevisionRef targetRevision) {
        if (targetRevision == null) {
            return TimelineCheckoutPlan.revisionNotFound(List.of(
                    new TimelineBranchOperationIssue(
                            TimelineBranchOperationIssueSeverity.ERROR,
                            TimelineBranchOperationIssueCode.INVALID_REVISION_REF,
                            "Target revision must not be null", Map.of())));
        }
        return TimelineCheckoutPlan.ready(null, targetRevision);
    }

    /**
     * Plan non-destructive rollback.
     */
    public TimelineRollbackPlan planRollback(
            TimelineRevisionRef currentRevision,
            TimelineRevisionRef targetRevision) {
        if (currentRevision == null || targetRevision == null) {
            return TimelineRollbackPlan.invalidTarget(List.of(
                    new TimelineBranchOperationIssue(
                            TimelineBranchOperationIssueSeverity.ERROR,
                            TimelineBranchOperationIssueCode.ROLLBACK_TARGET_NOT_FOUND,
                            "Current and target revisions must not be null", Map.of())));
        }
        if (currentRevision.equals(targetRevision)) {
            return TimelineRollbackPlan.noOp(currentRevision, targetRevision);
        }
        // Ancestry validation is future work when graph is available
        return TimelineRollbackPlan.ready(currentRevision, targetRevision);
    }

    /**
     * Plan branch switch.
     */
    public TimelineBranchSwitchPlan planSwitch(
            TimelineBranch source,
            TimelineBranch target,
            boolean hasUnsavedChanges) {
        if (source == null) {
            return TimelineBranchSwitchPlan.sourceBranchNotFound(List.of(
                    new TimelineBranchOperationIssue(
                            TimelineBranchOperationIssueSeverity.ERROR,
                            TimelineBranchOperationIssueCode.INVALID_BRANCH_ID,
                            "Source branch must not be null", Map.of())));
        }
        if (target == null) {
            return TimelineBranchSwitchPlan.targetBranchNotFound(List.of(
                    new TimelineBranchOperationIssue(
                            TimelineBranchOperationIssueSeverity.ERROR,
                            TimelineBranchOperationIssueCode.INVALID_BRANCH_ID,
                            "Target branch must not be null", Map.of())));
        }
        if (hasUnsavedChanges) {
            return TimelineBranchSwitchPlan.unsavedChangesRequireDecision(
                    source.name(), target.name(), List.of(
                            new TimelineBranchOperationIssue(
                                    TimelineBranchOperationIssueSeverity.WARNING,
                                    TimelineBranchOperationIssueCode.UNSAVED_CHANGES,
                                    "Unsaved changes require decision before switch", Map.of())));
        }
        return TimelineBranchSwitchPlan.ready(source.name(), target.name(), target.headRevision());
    }
}
