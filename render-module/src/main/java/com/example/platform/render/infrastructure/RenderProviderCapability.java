package com.example.platform.render.infrastructure;

import java.util.List;
import java.util.Set;

/**
 * Declared capabilities of a render provider.
 *
 * <p>Used by {@link RenderProviderRouter} and {@link RenderProviderRegistry}
 * for capability-based routing and health checking.</p>
 *
 * @param providerKey           unique identifier for this provider (e.g., "javacv", "ofx")
 * @param supportedFormats      output container formats (e.g., "mp4", "ogg", "webm")
 * @param supportedCodecs       video/audio codecs (e.g., "h264", "h265", "aac")
 * @param supportedEffects      effect keys this provider can handle
 * @param supportedTransitions  transition types supported
 * @param supportedSubtitleModes subtitle burn-in modes
 * @param maxResolution         maximum output resolution (e.g., "3840x2160")
 * @param requiresExternalBinary whether this provider needs an external binary
 * @param requiresGpu          whether GPU acceleration is required
 * @param experimental         whether this provider is experimental
 * @param availableInProfiles   profile names that can use this provider
 */
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
        Set<String> availableInProfiles
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
}
