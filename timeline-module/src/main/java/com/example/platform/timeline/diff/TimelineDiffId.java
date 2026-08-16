package com.example.platform.timeline.diff;

public record TimelineDiffId(String value) {
    public TimelineDiffId {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("TimelineDiffId must not be blank");
    }
}
