package com.example.platform.render.domain.timeline.version;

import java.util.Map;

/**
 * Timeline branch pointing to a head revision.
 * Internal domain model. No full history, no persistence.
 */
public record TimelineBranch(
        TimelineBranchId id,
        TimelineBranchName name,
        TimelineRevisionRef headRevision,
        Map<String, String> safeMetadata) {

    public TimelineBranch {
        if (id == null) throw new IllegalArgumentException("Branch ID must not be null");
        if (name == null) throw new IllegalArgumentException("Branch name must not be null");
        if (headRevision == null) throw new IllegalArgumentException("Head revision must not be null");
        if (safeMetadata == null) safeMetadata = Map.of();
        else safeMetadata = Map.copyOf(safeMetadata);
    }
}
