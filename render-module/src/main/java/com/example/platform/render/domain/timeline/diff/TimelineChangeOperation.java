package com.example.platform.render.domain.timeline.diff;

import java.util.Map;

/**
 * Single timeline change operation.
 * Internal domain model. Provider-neutral, storage-neutral.
 */
public record TimelineChangeOperation(
        TimelineChangeOperationId id,
        TimelineChangeType type,
        TimelineChangeScope scope,
        TimelineChangePath path,
        TimelineChangePayload beforeValue,
        TimelineChangePayload afterValue,
        Map<String, String> safeMetadata) {

    public TimelineChangeOperation {
        if (id == null) throw new IllegalArgumentException("Change operation ID must not be null");
        if (type == null) throw new IllegalArgumentException("Change type must not be null");
        if (scope == null) throw new IllegalArgumentException("Change scope must not be null");
        if (path == null) throw new IllegalArgumentException("Change path must not be null");
    }
}
