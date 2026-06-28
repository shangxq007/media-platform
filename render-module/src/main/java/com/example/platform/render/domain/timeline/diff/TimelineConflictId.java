package com.example.platform.render.domain.timeline.diff;

public record TimelineConflictId(String value) {
    public TimelineConflictId {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("TimelineConflictId must not be blank");
    }
}
