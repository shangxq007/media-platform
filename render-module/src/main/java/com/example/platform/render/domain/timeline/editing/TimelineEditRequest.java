package com.example.platform.render.domain.timeline.editing;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Request to apply one or more edit operations to a timeline.
 * Immutable record. Internal domain model.
 *
 * @param requestId   unique request identifier
 * @param timelineId  target timeline id
 * @param operations  ordered list of edit operations
 * @param safeMetadata safe metadata only
 */
public record TimelineEditRequest(
        String requestId,
        String timelineId,
        List<TimelineEditOperation> operations,
        Map<String, String> safeMetadata) {

    public TimelineEditRequest {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(timelineId, "timelineId must not be null");
        operations = operations == null ? List.of() : List.copyOf(operations);
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
    }
}
