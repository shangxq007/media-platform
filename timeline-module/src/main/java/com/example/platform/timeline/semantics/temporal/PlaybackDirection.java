package com.example.platform.timeline.semantics.temporal;

/**
 * TEMPORAL_MAPPING_FOUNDATION_V1 (R2): explicit typed playback direction.
 *
 * <p>Reverse is NEVER encoded as negative rate, provider flag, FFmpeg filter
 * string, or boolean alongside signed rate. Canonical direction is explicit
 * typed state.
 */
public enum PlaybackDirection {
    FORWARD,
    REVERSE
}
