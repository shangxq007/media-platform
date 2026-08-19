package com.example.platform.render.domain.renderplan;

import com.example.platform.shared.time.FrameRate;
import com.example.platform.shared.time.MediaTime;
import java.util.Objects;

/**
 * Exact sample window (start, end, frameRate). Half-open [start, end) unless it
 * is a freeze point, where a zero-length [pos, pos] window is allowed (C9/C11).
 * All time math is exact rational — no double.
 *
 * @param start     window start (inclusive, exact)
 * @param end       window end (exclusive, exact; equal to start only for freeze points)
 * @param frameRate exact rational frame rate for frame-conversion boundaries
 */
public record RenderSampleWindow(MediaTime start, MediaTime end, FrameRate frameRate) {

    public RenderSampleWindow {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        Objects.requireNonNull(frameRate, "frameRate");
        if (start.isGreaterThan(end)) {
            throw new IllegalArgumentException("sample window start must be <= end");
        }
    }

    /** True when this is a zero-length freeze point window [pos, pos]. */
    public boolean isFreezePoint() {
        return start.isEqualTo(end);
    }
}
