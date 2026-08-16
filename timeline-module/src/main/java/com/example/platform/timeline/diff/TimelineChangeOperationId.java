package com.example.platform.timeline.diff;

public record TimelineChangeOperationId(String value) {
    public TimelineChangeOperationId {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("TimelineChangeOperationId must not be blank");
    }
}
