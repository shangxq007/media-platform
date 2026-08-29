package com.example.platform.render.domain.transition;

/**
 * Policy for baseline transition planning.
 * Immutable. Internal domain model.
 *
 * <p>Controls whether POC/restricted transitions are allowed,
 * and how unsupported/missing-clip/non-adjacent cases are handled.</p>
 */
public record BaselineTransitionPolicy(
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
    public static BaselineTransitionPolicy conservative() {
        return new BaselineTransitionPolicy(
                false, false, true, true, true, true, true);
    }

    /**
     * Permissive policy for internal testing.
     */
    public static BaselineTransitionPolicy permissive() {
        return new BaselineTransitionPolicy(
                true, false, true, false, false, false, true);
    }
}
