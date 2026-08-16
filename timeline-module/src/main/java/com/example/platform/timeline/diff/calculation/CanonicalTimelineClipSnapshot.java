package com.example.platform.timeline.diff.calculation;

import com.example.platform.timeline.canonicalmodel.TimelineClipEffect;
import com.example.platform.shared.time.FrameRate;
import com.example.platform.shared.time.MediaTime;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Canonical semantic merge clip snapshot (C1-CNM1 exact-time contract).
 *
 * <p>All time fields are EXACT {@link MediaTime} (rational ticks/timeScale) —
 * integer milliseconds are a projection, never merge semantic authority.
 * Frame rate is the exact rational {@link FrameRate} of the clip's timeline
 * range; the denominator is preserved end-to-end through merge.
 *
 * <p>Effects are carried as an OPAQUE payload list ({@link TimelineClipEffect},
 * never semantically merged — preserved target/source-side per CNM1
 * effect-preservation contract). Unknown effect internals are not diffed.
 */
public record CanonicalTimelineClipSnapshot(
        String clipId,
        String assetBindingId,
        MediaTime start,
        MediaTime duration,
        MediaTime sourceStart,
        MediaTime sourceDuration,
        FrameRate rate,
        List<TimelineClipEffect> effects,
        Map<String, String> safeMetadata) {

    public CanonicalTimelineClipSnapshot {
        Objects.requireNonNull(clipId, "clipId");
        if (clipId.isBlank()) {
            throw new IllegalArgumentException("clipId must not be blank");
        }
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(duration, "duration");
        Objects.requireNonNull(sourceStart, "sourceStart");
        Objects.requireNonNull(sourceDuration, "sourceDuration");
        // MediaTime is non-negative by construction (ofTicks rejects ticks < 0);
        // durationMs >= 0 invariants are therefore guaranteed at type level.
        effects = effects == null ? List.of() : List.copyOf(effects);
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
    }

    /** True when the clip carries no effect payload. */
    public boolean hasNoEffects() {
        return effects.isEmpty();
    }
}
