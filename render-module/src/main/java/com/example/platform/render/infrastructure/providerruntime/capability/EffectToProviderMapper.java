package com.example.platform.render.infrastructure.providerruntime.capability;

import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Maps effects to required capabilities and capable providers.
 */
@Component
public class EffectToProviderMapper {

    private final Map<String, Set<String>> effectCapabilityMap = new HashMap<>();
    private final Map<String, Set<String>> capabilityProviderMap = new HashMap<>();

    public EffectToProviderMapper() {
        // Register effect -> capability mappings
        registerEffectCapability("video.blur", Set.of("video_processing", "blur"));
        registerEffectCapability("video.sharpen", Set.of("video_processing", "sharpen"));
        registerEffectCapability("video.color_grade", Set.of("video_processing", "color"));
        registerEffectCapability("video.fade_in", Set.of("video_processing", "transition"));
        registerEffectCapability("video.fade_out", Set.of("video_processing", "transition"));
        registerEffectCapability("video.transition", Set.of("video_processing", "transition"));
        registerEffectCapability("video.overlay", Set.of("video_processing", "overlay"));
        registerEffectCapability("video.crop", Set.of("video_processing", "spatial"));
        registerEffectCapability("video.resize", Set.of("video_processing", "spatial"));
        registerEffectCapability("video.speed", Set.of("video_processing", "temporal"));

        registerEffectCapability("audio.fade_in", Set.of("audio_processing", "transition"));
        registerEffectCapability("audio.fade_out", Set.of("audio_processing", "transition"));
        registerEffectCapability("audio.volume", Set.of("audio_processing", "volume"));
        registerEffectCapability("audio.mix", Set.of("audio_processing", "mix"));

        registerEffectCapability("subtitle.burn_in", Set.of("subtitle_processing", "render"));
        registerEffectCapability("subtitle.style", Set.of("subtitle_processing", "style"));

        // Register capability -> provider mappings
        registerCapabilityProvider("video_processing", Set.of(
                "FFmpegRenderProvider",
                "GStreamerRenderProvider",
                "NatronRenderProvider"
        ));

        registerCapabilityProvider("audio_processing", Set.of(
                "FFmpegRenderProvider",
                "GStreamerRenderProvider"
        ));

        registerCapabilityProvider("subtitle_processing", Set.of(
                "FFmpegRenderProvider",
                "LibassSubtitleProvider"
        ));

        registerCapabilityProvider("packaging", Set.of(
                "GPACRenderProvider",
                "Bento4PackagingProvider",
                "ShakaPackagingProvider"
        ));
    }

    /**
     * Register an effect -> capability mapping.
     */
    public void registerEffectCapability(String effectKey, Set<String> capabilities) {
        effectCapabilityMap.put(effectKey, capabilities);
    }

    /**
     * Register a capability -> provider mapping.
     */
    public void registerCapabilityProvider(String capability, Set<String> providers) {
        capabilityProviderMap.put(capability, providers);
    }

    /**
     * Get the required capabilities for an effect.
     */
    public Set<String> getRequiredCapabilities(String effectKey) {
        return effectCapabilityMap.getOrDefault(effectKey, Set.of());
    }

    /**
     * Get the providers that support a capability.
     */
    public Set<String> getProvidersForCapability(String capability) {
        return capabilityProviderMap.getOrDefault(capability, Set.of());
    }

    /**
     * Get the providers that can handle an effect.
     */
    public Set<String> getProvidersForEffect(String effectKey) {
        Set<String> capabilities = getRequiredCapabilities(effectKey);
        Set<String> providers = new HashSet<>();

        for (String capability : capabilities) {
            providers.addAll(getProvidersForCapability(capability));
        }

        return providers;
    }

    /**
     * Check if a provider can handle an effect.
     */
    public boolean canProviderHandleEffect(String providerName, String effectKey) {
        Set<String> capableProviders = getProvidersForEffect(effectKey);
        return capableProviders.contains(providerName);
    }

    /**
     * Get all registered effects.
     */
    public Set<String> getAllEffects() {
        return effectCapabilityMap.keySet();
    }

    /**
     * Get all registered capabilities.
     */
    public Set<String> getAllCapabilities() {
        return capabilityProviderMap.keySet();
    }
}
