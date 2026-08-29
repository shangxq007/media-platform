package com.example.platform.render.domain.planning;

/** Final timeline composition strategy. */
public enum FinalComposerHint {
    AUTO,
    MLT,

    /**
     * Requires a later binding to a concrete typed provider plugin. This is a
     * semantic strategy and never serves as a backend or provider identity.
     */
    TYPED_PROVIDER_PLUGIN;

    public static FinalComposerHint fromString(String value) {
        if (value == null || value.isBlank()) {
            return AUTO;
        }
        return switch (value.toLowerCase()) {
            case "mlt" -> MLT;
            case "typed_provider_plugin" -> TYPED_PROVIDER_PLUGIN;
            default -> AUTO;
        };
    }
}
