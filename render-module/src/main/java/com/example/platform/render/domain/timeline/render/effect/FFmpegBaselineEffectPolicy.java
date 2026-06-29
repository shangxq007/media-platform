package com.example.platform.render.domain.timeline.render.effect;

/**
 * Policy for FFmpeg baseline effect planning.
 * Immutable. Internal domain model.
 *
 * <p>Controls whether POC/restricted effects are allowed,
 * and how unsupported/missing-target cases are handled.</p>
 */
public record FFmpegBaselineEffectPolicy(
        boolean allowPocEffects,
        boolean allowRestrictedEffects,
        boolean allowWarnings,
        boolean failOnUnsupported,
        boolean failOnMissingTarget
) {
    /**
     * Conservative default policy.
     */
    public static FFmpegBaselineEffectPolicy conservative() {
        return new FFmpegBaselineEffectPolicy(false, false, true, true, true);
    }

    /**
     * Permissive policy for internal testing.
     */
    public static FFmpegBaselineEffectPolicy permissive() {
        return new FFmpegBaselineEffectPolicy(true, false, true, false, false);
    }
}
