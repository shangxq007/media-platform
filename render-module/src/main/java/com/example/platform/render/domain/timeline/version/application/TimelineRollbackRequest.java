package com.example.platform.render.domain.timeline.version.application;

import com.example.platform.render.domain.timeline.version.TimelineBranchName;
import com.example.platform.render.domain.timeline.version.TimelineRevisionRef;
import java.util.Map;

/**
 * Request for timeline rollback planning.
 * Internal domain model.
 */
public record TimelineRollbackRequest(
        TimelineRollbackRequestId id,
        TimelineRevisionRef currentRevision,
        TimelineRevisionRef targetRevision,
        TimelineBranchName branchName,
        boolean requireTargetAncestor,
        Map<String, String> safeMetadata
) {
    public TimelineRollbackRequest {
        if (id == null) throw new IllegalArgumentException("Request id must not be null");
        if (currentRevision == null) throw new IllegalArgumentException("Current revision must not be null");
        if (targetRevision == null) throw new IllegalArgumentException("Target revision must not be null");
        if (safeMetadata == null) safeMetadata = Map.of();
        else safeMetadata = Map.copyOf(safeMetadata);
    }
}
