package com.example.platform.render.domain.timeline.diff;

import java.util.Map;

/**
 * A conflict between timeline changes.
 * Internal domain model. No stack traces, no provider/storage details.
 */
public record TimelineConflict(
        TimelineConflictId id,
        TimelineConflictType type,
        TimelineConflictSeverity severity,
        TimelineChangePath path,
        String message,
        Map<String, String> safeMetadata) {

    public TimelineConflict {
        if (id == null) throw new IllegalArgumentException("Conflict ID must not be null");
        if (type == null) throw new IllegalArgumentException("Conflict type must not be null");
        if (severity == null) throw new IllegalArgumentException("Conflict severity must not be null");
    }

    public boolean isBlocking() {
        return severity == TimelineConflictSeverity.BLOCKING;
    }
}
