package com.example.platform.render.domain.timeline.diff;

/**
 * Safe path identifying the location of a change in the timeline structure.
 * Internal domain model. String-based path (e.g., "tracks[0].clips[2].startMs").
 */
public record TimelineChangePath(String value) {
    public TimelineChangePath {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("TimelineChangePath must not be blank");
    }
}
