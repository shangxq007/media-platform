package com.example.platform.render.domain.renderplan;

import com.example.platform.shared.time.FrameRate;
import com.example.platform.shared.time.MediaTime;
import java.util.Objects;

/**
 * Exact render extent (C9): half-open [start, end) temporal interval plus an exact
 * rational frame rate. Execution request/planning semantics — NOT authored
 * Timeline semantics. start < end REQUIRED (else invalid).
 *
 * @param start     extent start (inclusive, exact)
 * @param end       extent end (exclusive, exact)
 * @param frameRate exact rational frame rate
 */
public record RenderExtent(MediaTime start, MediaTime end, FrameRate frameRate) {

    public RenderExtent {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        Objects.requireNonNull(frameRate, "frameRate");
        // NOTE: start < end is NOT enforced here. RenderExtent is a plain value
        // object; the planner validates the extent (C9) and emits an
        // INVALID_RENDER_EXTENT diagnostic when start >= end. Validating here
        // would make that diagnostic unreachable (the record could not be built).
    }

    /** True when the extent is temporally valid: start < end (exact rational comparison). */
    public boolean isValid() {
        return start.isLessThan(end);
    }
}
