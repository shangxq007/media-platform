package com.example.platform.render.domain.timeline.version.application;

import com.example.platform.render.domain.timeline.version.TimelineBranchName;
import java.util.Map;

/**
 * Request for timeline branch switch.
 * Internal domain model.
 */
public record TimelineBranchSwitchRequest(
        TimelineBranchSwitchRequestId id,
        TimelineBranchName sourceBranch,
        TimelineBranchName targetBranch,
        boolean hasUnsavedChanges,
        Map<String, String> safeMetadata
) {
    public TimelineBranchSwitchRequest {
        if (id == null) throw new IllegalArgumentException("Request id must not be null");
        if (sourceBranch == null) throw new IllegalArgumentException("Source branch must not be null");
        if (targetBranch == null) throw new IllegalArgumentException("Target branch must not be null");
        if (safeMetadata == null) safeMetadata = Map.of();
        else safeMetadata = Map.copyOf(safeMetadata);
    }
}
