package com.example.platform.render.domain.timeline.semantics.temporal;

import com.example.platform.shared.time.MediaTime;
import java.util.Objects;

/**
 * TEMPORAL_MAPPING_FOUNDATION_V1 (TM12): freeze/hold — one exact source
 * position/instant occupies a positive Timeline occupied duration.
 *
 * <p>Owns ONLY the exact source position. It does NOT own placement duration
 * (Timeline placement authority), a second sourceRange, or FFmpeg filter
 * configuration. Freeze is never rate=0 and never a fake sourceRange
 * (TM12/R3).
 *
 * <p>Video: holds the defined source visual sample/frame. Audio freeze is NOT
 * inferred (audio non-identity execution fails closed, R4).
 */
public record FreezeTemporalMapping(MediaTime sourcePosition) implements TemporalMapping {

    public FreezeTemporalMapping {
        Objects.requireNonNull(sourcePosition, "sourcePosition");
    }

    @Override
    public Kind kind() {
        return Kind.FREEZE;
    }
}
