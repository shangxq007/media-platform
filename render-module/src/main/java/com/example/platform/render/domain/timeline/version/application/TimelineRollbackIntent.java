package com.example.platform.render.domain.timeline.version.application;

import com.example.platform.render.domain.timeline.version.TimelineCommitType;
import com.example.platform.render.domain.timeline.version.TimelineRevisionRef;
import java.util.Map;

/**
 * Planned rollback intent. Non-destructive.
 * Internal domain model. Does not persist, apply patch, or render.
 */
public record TimelineRollbackIntent(
        TimelineCommitType plannedCommitType,
        TimelineRevisionRef currentRevision,
        TimelineRevisionRef targetRevision,
        String message,
        Map<String, String> safeMetadata
) {
    public TimelineRollbackIntent {
        if (plannedCommitType == null) plannedCommitType = TimelineCommitType.ROLLBACK;
        if (safeMetadata == null) safeMetadata = Map.of();
        else safeMetadata = Map.copyOf(safeMetadata);
    }
}
