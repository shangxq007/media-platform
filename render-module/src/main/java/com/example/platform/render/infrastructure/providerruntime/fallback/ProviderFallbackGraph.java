package com.example.platform.render.infrastructure.providerruntime.fallback;

import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Defines the fallback graph for render providers.
 * Each provider has a chain of fallback providers to try if it fails.
 */
@Component
public class ProviderFallbackGraph {

    private final Map<String, List<String>> fallbackChains = new HashMap<>();

    public ProviderFallbackGraph() {
        // Default fallback chains
        // FFmpeg is the primary provider for most operations
        registerFallbackChain("FFmpegRenderProvider", List.of(
                "GStreamerRenderProvider",
                "GPACRenderProvider",
                "Bento4PackagingProvider"
        ));

        // GStreamer falls back to FFmpeg
        registerFallbackChain("GStreamerRenderProvider", List.of(
                "FFmpegRenderProvider",
                "GPACRenderProvider"
        ));

        // GPAC falls back to FFmpeg
        registerFallbackChain("GPACRenderProvider", List.of(
                "FFmpegRenderProvider",
                "GStreamerRenderProvider"
        ));

        // Bento4 falls back to Shaka
        registerFallbackChain("Bento4PackagingProvider", List.of(
                "ShakaPackagingProvider",
                "GPACRenderProvider"
        ));

        // Shaka falls back to Bento4
        registerFallbackChain("ShakaPackagingProvider", List.of(
                "Bento4PackagingProvider",
                "GPACRenderProvider"
        ));

        // Natron falls back to FFmpeg
        registerFallbackChain("NatronRenderProvider", List.of(
                "FFmpegRenderProvider"
        ));

        // Blender falls back to FFmpeg
        registerFallbackChain("BlenderRenderProvider", List.of(
                "FFmpegRenderProvider"
        ));

        // VapourSynth falls back to FFmpeg
        registerFallbackChain("VapourSynthRenderProvider", List.of(
                "FFmpegRenderProvider"
        ));

        // RemoteRender falls back to FFmpeg (when no remote workers available)
        registerFallbackChain("RemoteRenderProvider", List.of(
                "FFmpegRenderProvider"
        ));
    }

    /**
     * Register a fallback chain for a provider.
     */
    public void registerFallbackChain(String providerName, List<String> fallbacks) {
        fallbackChains.put(providerName, new ArrayList<>(fallbacks));
    }

    /**
     * Get the fallback chain for a provider.
     */
    public List<String> getFallbackChain(String providerName) {
        return fallbackChains.getOrDefault(providerName, List.of());
    }

    /**
     * Add a fallback to the beginning of a provider's chain.
     */
    public void addFallbackPrepend(String providerName, String fallbackProvider) {
        fallbackChains.computeIfAbsent(providerName, k -> new ArrayList<>())
                .add(0, fallbackProvider);
    }

    /**
     * Add a fallback to the end of a provider's chain.
     */
    public void addFallbackAppend(String providerName, String fallbackProvider) {
        fallbackChains.computeIfAbsent(providerName, k -> new ArrayList<>())
                .add(fallbackProvider);
    }

    /**
     * Remove a fallback from a provider's chain.
     */
    public void removeFallback(String providerName, String fallbackProvider) {
        List<String> chain = fallbackChains.get(providerName);
        if (chain != null) {
            chain.remove(fallbackProvider);
        }
    }

    /**
     * Get all registered fallback chains.
     */
    public Map<String, List<String>> getAllChains() {
        return Map.copyOf(fallbackChains);
    }

    /**
     * Check if a provider has any fallbacks.
     */
    public boolean hasFallbacks(String providerName) {
        List<String> chain = fallbackChains.get(providerName);
        return chain != null && !chain.isEmpty();
    }

    /**
     * Get the full fallback path for a provider, including the provider itself.
     */
    public List<String> getFullFallbackPath(String providerName) {
        List<String> path = new ArrayList<>();
        path.add(providerName);
        path.addAll(getFallbackChain(providerName));
        return path;
    }
}
