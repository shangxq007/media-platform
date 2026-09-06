package com.example.platform.providerplugin.visual;

/** Provider-runtime fallback outcome for an unsupported visual capability. */
public enum ProviderVisualFallbackBehavior {
    NO_FALLBACK, CUT, FADE_OUT_IN, DISABLE_EFFECT, REJECT_REQUEST,
    MANUAL_REVIEW_REQUIRED, PROVIDER_SPECIFIC_ONLY
}
