package com.example.platform.render.domain.timeline.version;

import java.util.Map;

/**
 * Branch head pointer.
 * Internal domain model. Moving pointer is semantic planning only, no persistence.
 */
public record TimelineBranchPointer(
        TimelineBranchId branchId,
        TimelineRevisionRef headRevision,
        TimelineCommitId headCommitId,
        Map<String, String> safeMetadata) {

    public TimelineBranchPointer {
        if (branchId == null) throw new IllegalArgumentException("Branch ID must not be null");
        if (headRevision == null) throw new IllegalArgumentException("Head revision must not be null");
        if (safeMetadata == null) safeMetadata = Map.of();
        else safeMetadata = Map.copyOf(safeMetadata);
    }

    /**
     * Create a new pointer with updated head (semantic move, no mutation).
     */
    public TimelineBranchPointer moveTo(TimelineRevisionRef newRevision, TimelineCommitId newCommitId) {
        return new TimelineBranchPointer(branchId, newRevision, newCommitId, safeMetadata);
    }
}
