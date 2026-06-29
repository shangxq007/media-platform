package com.example.platform.render.domain.timeline.diff.merge.plan;

import com.example.platform.render.domain.timeline.diff.calculation.CanonicalTimelineSnapshot;
import java.util.Map;

/**
 * Request for non-conflicting merge plan generation.
 * Internal domain model. Provider-neutral, storage-neutral.
 */
public record TimelineMergePlanRequest(
        TimelineMergePlanRequestId id,
        CanonicalTimelineSnapshot base,
        CanonicalTimelineSnapshot ours,
        CanonicalTimelineSnapshot theirs,
        TimelineMergePlanPolicy policy,
        Map<String, String> safeMetadata) {

    public TimelineMergePlanRequest {
        if (id == null) throw new IllegalArgumentException("Request ID must not be null");
        if (safeMetadata == null) safeMetadata = Map.of();
    }

    /**
     * Returns the effective policy, defaulting to CONSERVATIVE if null.
     */
    public TimelineMergePlanPolicy effectivePolicy() {
        return policy != null ? policy : TimelineMergePlanPolicy.CONSERVATIVE;
    }
}
