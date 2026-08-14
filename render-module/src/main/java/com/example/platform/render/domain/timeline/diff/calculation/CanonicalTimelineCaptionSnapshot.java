package com.example.platform.render.domain.timeline.diff.calculation;

import com.example.platform.shared.time.MediaTime;

import java.util.Map;
import java.util.Objects;

/**
 * Canonical semantic merge caption snapshot (C1-CNM1 exact-time contract).
 *
 * <p>{@code start}/{@code end} are exact {@link MediaTime}; integer
 * milliseconds are a projection, never merge semantic authority.
 */
public record CanonicalTimelineCaptionSnapshot(
        String captionId,
        MediaTime start,
        MediaTime end,
        String text,
        Map<String, String> style,
        Map<String, String> safeMetadata) {

    public CanonicalTimelineCaptionSnapshot {
        if (captionId == null || captionId.isBlank())
            throw new IllegalArgumentException("captionId must not be blank");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (end.isLessThan(start)) throw new IllegalArgumentException("end must be >= start");
    }
}
