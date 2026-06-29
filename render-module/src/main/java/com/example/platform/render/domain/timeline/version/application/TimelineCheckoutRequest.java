package com.example.platform.render.domain.timeline.version.application;

import java.util.Map;

/**
 * Request for timeline checkout.
 * Internal domain model.
 */
public record TimelineCheckoutRequest(
        TimelineCheckoutRequestId id,
        TimelineCheckoutTarget target,
        Map<String, String> safeMetadata
) {
    public TimelineCheckoutRequest {
        if (id == null) throw new IllegalArgumentException("Request id must not be null");
        if (target == null) throw new IllegalArgumentException("Target must not be null");
        if (safeMetadata == null) safeMetadata = Map.of();
        else safeMetadata = Map.copyOf(safeMetadata);
    }
}
