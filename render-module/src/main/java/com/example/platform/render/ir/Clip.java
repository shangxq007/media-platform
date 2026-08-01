package com.example.platform.render.ir;

import java.util.Objects;

/**
 * A clip within a video track, referencing a source range and positioned on the timeline.
 */
public record Clip(String id, SourceRange source, RationalTime timelineStart) {
    public Clip {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(timelineStart, "timelineStart must not be null");
    }
}
