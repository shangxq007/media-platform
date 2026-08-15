package com.example.platform.render.domain.timeline.semantics.temporal;

import com.example.platform.render.domain.timeline.semantics.clip.MediaClip;
import com.example.platform.shared.time.MediaTime;
import java.util.Objects;

/**
 * TEMPORAL_MAPPING_FOUNDATION_V1 (R4/TM23): audio temporal execution guard.
 *
 * <p>TemporalMapping defines time traversal; it does NOT define audible
 * pitch/time-stretch semantics (Audio domain owns that, deferred). Therefore:
 * identity audio execution (ConstantRate 1/1 FORWARD) is supported; ANY
 * non-identity audio temporal mapping FAILS CLOSED before FFmpeg execution —
 * never silently atempo/areverse/resample/drop, and never a repeated audio
 * sample for freeze.
 */
public final class TemporalAudioExecutionGuard {

    private TemporalAudioExecutionGuard() {
    }

    /** True when the mapping is audio-executable (identity only, R4). */
    public static boolean isAudioExecutable(TemporalMapping mapping) {
        Objects.requireNonNull(mapping, "mapping");
        return mapping instanceof ConstantRateTemporalMapping cm
                && cm.rate().numerator() == 1
                && cm.rate().denominator() == 1
                && cm.direction() == PlaybackDirection.FORWARD;
    }

    /**
     * Fail-closed check: throws when an audio object carries a non-identity
     * temporal mapping. Deterministic classification for callers.
     */
    public static void requireAudioIdentity(TemporalMapping mapping) {
        if (!isAudioExecutable(mapping)) {
            String kind = mapping instanceof ConstantRateTemporalMapping cm
                    ? "constant-rate " + cm.rate().numerator() + "/" + cm.rate().denominator()
                        + " " + cm.direction()
                    : "freeze";
            throw new AudioTemporalExecutionUnsupported(
                    "audio temporal execution unsupported: " + kind
                            + " (audio pitch/time-stretch policy deferred to Audio domain;"
                            + " identity 1/1 FORWARD is the only audio-executable mapping)");
        }
    }

    /** Deterministic error classification (R4): never delegated to FFmpeg discovery. */
    public static final class AudioTemporalExecutionUnsupported extends RuntimeException {
        public AudioTemporalExecutionUnsupported(String message) {
            super(message);
        }
    }
}
