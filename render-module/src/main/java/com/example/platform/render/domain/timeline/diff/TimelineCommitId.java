package com.example.platform.render.domain.timeline.diff;

public record TimelineCommitId(String value) {
    public TimelineCommitId {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("TimelineCommitId must not be blank");
    }
}
