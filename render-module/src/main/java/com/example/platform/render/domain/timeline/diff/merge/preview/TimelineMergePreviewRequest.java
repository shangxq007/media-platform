package com.example.platform.render.domain.timeline.diff.merge.preview;

import com.example.platform.render.domain.timeline.diff.calculation.CanonicalTimelineSnapshot;
import java.util.Map;

/**
 * Request for a merge preview.
 * Internal domain model. Provider-neutral, storage-neutral.
 */
public record TimelineMergePreviewRequest(
        TimelineMergePreviewRequestId id,
        CanonicalTimelineSnapshot base,
        CanonicalTimelineSnapshot ours,
        CanonicalTimelineSnapshot theirs,
        TimelineMergePreviewMode mode,
        TimelineMergePreviewPolicy policy,
        Map<String, String> safeMetadata) {

    public TimelineMergePreviewRequest {
        if (id == null) throw new IllegalArgumentException("Request ID must not be null");
    }

    /**
     * Returns the effective mode, defaulting to DIFF_AND_CONFLICTS if null.
     */
    public TimelineMergePreviewMode effectiveMode() {
        return mode != null ? mode : TimelineMergePreviewMode.DIFF_AND_CONFLICTS;
    }

    /**
     * Returns the effective policy, defaulting to CONSERVATIVE if null.
     */
    public TimelineMergePreviewPolicy effectivePolicy() {
        return policy != null ? policy : TimelineMergePreviewPolicy.CONSERVATIVE;
    }
}
