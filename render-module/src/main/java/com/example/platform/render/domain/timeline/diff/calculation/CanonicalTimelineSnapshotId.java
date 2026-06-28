package com.example.platform.render.domain.timeline.diff.calculation;

public record CanonicalTimelineSnapshotId(String value) {
    public CanonicalTimelineSnapshotId {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("CanonicalTimelineSnapshotId must not be blank");
    }
}
