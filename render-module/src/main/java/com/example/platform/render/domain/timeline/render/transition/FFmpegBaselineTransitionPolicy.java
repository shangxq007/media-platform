package com.example.platform.render.domain.timeline.render.transition;

/**
 * Policy for FFmpeg baseline transition planning.
 * Immutable. Internal domain model.
 *
 * <p>Controls whether POC/restricted transitions are allowed,
 * and how unsupported/missing-clip/non-adjacent cases are handled.</p>
 */
public record FFmpegBaselineTransitionPolicy(
        boolean allowPocTransitions,
        boolean allowRestrictedTransitions,
        boolean allowWarnings,
        boolean failOnUnsupported,
        boolean failOnMissingClip,
        boolean failOnNonAdjacentClips,
        boolean allowCutWithZeroDuration
) {
    /**
     * Conservative default policy.
     */
    public static FFmpegBaselineTransitionPolicy conservative() {
        return new FFmpegBaselineTransitionPolicy(
                false, false, true, true, true, true, true);
    }

    /**
     * Permissive policy for internal testing.
     */
    public static FFmpegBaselineTransitionPolicy permissive() {
        return new FFmpegBaselineTransitionPolicy(
                true, false, true, false, false, false, true);
    }
}
