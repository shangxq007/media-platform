package com.example.platform.render.infrastructure;

import java.util.List;
import java.util.Set;

public record RenderProviderCapability(
        String providerKey,
        Set<String> supportedFormats,
        Set<String> supportedCodecs,
        Set<String> supportedEffects,
        Set<String> supportedTransitions,
        Set<String> supportedSubtitleModes,
        String maxResolution,
        boolean requiresExternalBinary,
        boolean requiresGpu,
        boolean experimental,
        Set<String> availableInProfiles,
        ProviderStatus status,
        String priority,
        ProviderType providerType,
        String purpose,
        List<String> limitations,
        boolean autoDispatch
) {
    public boolean supportsFormat(String format) {
        return supportedFormats.contains(format);
    }

    public boolean supportsCodec(String codec) {
        return supportedCodecs.contains(codec);
    }

    public boolean supportsEffect(String effectKey) {
        return supportedEffects.contains(effectKey);
    }

    public boolean supportsTransition(String transition) {
        return supportedTransitions.contains(transition);
    }

    public boolean availableForProfile(String profile) {
        return availableInProfiles.contains(profile);
    }

    public boolean isAutoDispatch() {
        return autoDispatch;
    }

    public boolean isProduction() {
        return status == ProviderStatus.PRODUCTION;
    }

    public boolean isPoc() {
        return status == ProviderStatus.POC;
    }

    public boolean isDeprecated() {
        return status == ProviderStatus.DEPRECATED;
    }

    public boolean isHold() {
        return status == ProviderStatus.HOLD;
    }

    public boolean isSpike() {
        return status == ProviderStatus.SPIKE;
    }

    public boolean isOptional() {
        return status == ProviderStatus.OPTIONAL;
    }

    public boolean participatesInAutoRouting() {
        return autoDispatch && (status == ProviderStatus.PRODUCTION || status == ProviderStatus.POC);
    }

    public static RenderProviderCapability legacy(
            String providerKey,
            Set<String> supportedFormats,
            Set<String> supportedCodecs,
            Set<String> supportedEffects,
            Set<String> supportedTransitions,
            Set<String> supportedSubtitleModes,
            String maxResolution,
            boolean requiresExternalBinary,
            boolean requiresGpu,
            boolean experimental,
            Set<String> availableInProfiles) {
        return new RenderProviderCapability(
                providerKey, supportedFormats, supportedCodecs,
                supportedEffects, supportedTransitions, supportedSubtitleModes,
                maxResolution, requiresExternalBinary, requiresGpu, experimental,
                availableInProfiles,
                ProviderStatus.POC, "P2", ProviderType.RENDER,
                "Legacy provider", List.of(), true);
    }
}
