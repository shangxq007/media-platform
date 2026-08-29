package com.example.platform.render.domain.plan;

/**
 * Policy for basic render planning.
 * Immutable. Internal domain model.
 *
 * <p>Controls warnings, POC handling, output profile validation,
 * and overlay validation requirements.</p>
 */
public record BasicRenderPolicy(
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
    public static BasicRenderPolicy conservative() {
        return new BasicRenderPolicy(
                true, false, false, false, false, false, true, true, true);
    }

    /**
     * Permissive policy for internal testing.
     */
    public static BasicRenderPolicy permissive() {
        return new BasicRenderPolicy(
                true, true, true, false, false, false, false, false, false);
    }
}
