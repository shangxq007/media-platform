package com.example.platform.render.domain.effect;

/**
 * Policy for baseline effect planning.
 * Immutable. Internal domain model.
 *
 * <p>Controls whether POC/restricted effects are allowed,
 * and how unsupported/missing-target cases are handled.</p>
 */
public record BaselineEffectPolicy(
        boolean allowPocEffects,
        boolean allowRestrictedEffects,
        boolean allowWarnings,
        boolean failOnUnsupported,
        boolean failOnMissingTarget
) {
    /**
     * Conservative default policy.
     */
    public static BaselineEffectPolicy conservative() {
        return new BaselineEffectPolicy(false, false, true, true, true);
    }

    /**
     * Permissive policy for internal testing.
     */
    public static BaselineEffectPolicy permissive() {
        return new BaselineEffectPolicy(true, false, true, false, false);
    }
}
