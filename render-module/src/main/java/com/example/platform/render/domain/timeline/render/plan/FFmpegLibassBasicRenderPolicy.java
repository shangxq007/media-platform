package com.example.platform.render.domain.timeline.render.plan;

/**
 * Policy for FFmpeg/libass basic render planning.
 * Immutable. Internal domain model.
 *
 * <p>Controls warnings, POC handling, output profile validation,
 * and overlay validation requirements.</p>
 */
public record FFmpegLibassBasicRenderPolicy(
        boolean allowWarnings,
        boolean allowPocEffects,
        boolean allowPocTransitions,
        boolean failOnTimelineWarnings,
        boolean failOnEffectWarnings,
        boolean failOnTransitionWarnings,
        boolean failOnUnsupportedOutputProfile,
        boolean requireCaptionOverlayValidation,
        boolean requireWatermarkOverlayValidation
) {
    /**
     * Conservative default policy.
     */
    public static FFmpegLibassBasicRenderPolicy conservative() {
        return new FFmpegLibassBasicRenderPolicy(
                true, false, false, false, false, false, true, true, true);
    }

    /**
     * Permissive policy for internal testing.
     */
    public static FFmpegLibassBasicRenderPolicy permissive() {
        return new FFmpegLibassBasicRenderPolicy(
                true, true, true, false, false, false, false, false, false);
    }
}
