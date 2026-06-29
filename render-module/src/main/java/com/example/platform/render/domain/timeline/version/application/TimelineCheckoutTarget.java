package com.example.platform.render.domain.timeline.version.application;

import com.example.platform.render.domain.timeline.version.TimelineBranchName;
import com.example.platform.render.domain.timeline.version.TimelineCommitId;
import com.example.platform.render.domain.timeline.version.TimelineRevisionRef;
import java.util.Map;

/**
 * Checkout target specifying branch, revision, or commit.
 * Internal domain model.
 */
public record TimelineCheckoutTarget(
        TimelineCheckoutTargetType type,
        TimelineBranchName branchName,
        TimelineRevisionRef revisionRef,
        TimelineCommitId commitId,
        Map<String, String> safeMetadata
) {
    public TimelineCheckoutTarget {
        if (type == null) throw new IllegalArgumentException("Target type must not be null");
        if (safeMetadata == null) safeMetadata = Map.of();
        else safeMetadata = Map.copyOf(safeMetadata);
    }

    public static TimelineCheckoutTarget ofBranch(TimelineBranchName branchName) {
        return new TimelineCheckoutTarget(
                TimelineCheckoutTargetType.BRANCH, branchName, null, null, Map.of());
    }

    public static TimelineCheckoutTarget ofRevision(TimelineRevisionRef revisionRef) {
        return new TimelineCheckoutTarget(
                TimelineCheckoutTargetType.REVISION, null, revisionRef, null, Map.of());
    }

    public static TimelineCheckoutTarget ofCommit(TimelineCommitId commitId) {
        return new TimelineCheckoutTarget(
                TimelineCheckoutTargetType.COMMIT, null, null, commitId, Map.of());
    }
}
