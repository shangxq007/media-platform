package com.example.platform.render.domain.timeline.diff;

public record TimelinePatchId(String value) {
    public TimelinePatchId {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("TimelinePatchId must not be blank");
    }
}
