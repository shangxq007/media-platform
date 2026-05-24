package com.example.platform.render.infrastructure;

import com.example.platform.render.domain.timeline.FinalComposerHint;
import com.example.platform.render.domain.timeline.TimelineExtensions;
import com.example.platform.render.domain.timeline.TimelineSpec;
import com.example.platform.render.domain.timeline.TimelineTrack;
import com.example.platform.render.domain.timeline.TimelineClipEffect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Unified provider resolver that combines timeline-aware composer selection
 * with health-aware provider fallback.
 *
 * <p>Replaces the separate {@code FinalComposerSelector} + {@code RenderProviderFallbackPolicy}
 * to ensure health fallback is aware of why a specific backend was chosen.</p>
 */
@Component
public class RenderProviderResolver {

    private static final Logger log = LoggerFactory.getLogger(RenderProviderResolver.class);

    private final RenderProviderRegistry registry;
    private final RenderProviderSelectionPolicy selectionPolicy;

    @Value("${render.timeline.mlt-multitrack-min-tracks:2}")
    private int mltMultitrackMinTracks = 2;

    public RenderProviderResolver(RenderProviderRegistry registry,
                                   RenderProviderSelectionPolicy selectionPolicy) {
        this.registry = registry;
        this.selectionPolicy = selectionPolicy;
    }

    public record ResolvedProvider(
            RenderProvider provider,
            String backendKey,
            FinalComposerHint composerHint,
            boolean degraded,
            String degradeReason) {}

    /**
     * Resolve the best provider for a render job, considering both timeline
     * features and provider health.
     */
    public ResolvedProvider resolve(TimelineSpec timeline, TimelineExtensions extensions,
                                     String profile, List<String> effectKeys) {
        FinalComposerHint hint = selectComposer(timeline, extensions);
        String preferredBackend = backendKey(hint);

        // Step 1: Try preferred provider (timeline-aware selection)
        Optional<RenderProvider> preferred = registry.getProvider(preferredBackend);
        if (preferred.isPresent()) {
            RenderProviderHealthCheck health = registry.getHealthCheck(preferredBackend);
            if (health == null || health.healthy()) {
                log.debug("Using {} provider for profile '{}' (composer hint: {})",
                        preferredBackend, profile, hint);
                return new ResolvedProvider(preferred.get(), preferredBackend, hint, false, null);
            }
            log.warn("Preferred provider '{}' is unhealthy, falling back", preferredBackend);
        }

        // Step 2: Try any healthy provider that supports the profile
        List<RenderProviderCapability> allCaps = registry.getCapabilitiesForProfile(profile);
        for (RenderProviderCapability cap : allCaps) {
            RenderProviderHealthCheck health = registry.getHealthCheck(cap.providerKey());
            if (health != null && health.healthy()) {
                Optional<RenderProvider> provider = registry.getProvider(cap.providerKey());
                if (provider.isPresent()) {
                    boolean degraded = !cap.providerKey().equals(preferredBackend);
                    String reason = degraded
                            ? "Preferred " + preferredBackend + " unavailable, using " + cap.providerKey()
                            : null;
                    if (degraded) {
                        log.warn("Degraded fallback: {} → {} for profile '{}'",
                                preferredBackend, cap.providerKey(), profile);
                    }
                    return new ResolvedProvider(provider.get(), cap.providerKey(), hint, degraded, reason);
                }
            }
        }

        // Step 3: Try any provider (unhealthy, last resort)
        for (RenderProviderCapability cap : allCaps) {
            Optional<RenderProvider> provider = registry.getProvider(cap.providerKey());
            if (provider.isPresent()) {
                log.error("Last resort: using unhealthy provider '{}' for profile '{}'",
                        cap.providerKey(), profile);
                return new ResolvedProvider(provider.get(), cap.providerKey(), hint, true,
                        "All providers unhealthy, using " + cap.providerKey());
            }
        }

        throw new IllegalStateException("No render provider available for profile: " + profile);
    }

    /**
     * Select the best composer hint based on timeline features.
     */
    public FinalComposerHint selectComposer(TimelineSpec timeline, TimelineExtensions extensions) {
        if (extensions != null && extensions.finalComposer() != FinalComposerHint.AUTO) {
            return extensions.finalComposer();
        }
        if (countVideoTracks(timeline) >= mltMultitrackMinTracks) {
            return FinalComposerHint.MLT;
        }
        if (extensions != null && !extensions.externalRenderNodes().isEmpty()) {
            return FinalComposerHint.MLT;
        }
        if (hasAlphaExternalLayers(extensions)) {
            return FinalComposerHint.MLT;
        }
        if (hasCrossDissolveOrComplexTransition(timeline)) {
            return FinalComposerHint.MLT;
        }
        if (hasMultitrackAudioMix(timeline)) {
            return FinalComposerHint.MLT;
        }
        return FinalComposerHint.FFMPEG;
    }

    public String backendKey(FinalComposerHint hint) {
        return hint == FinalComposerHint.MLT ? "mlt" : "ffmpeg";
    }

    private int countVideoTracks(TimelineSpec timeline) {
        if (timeline.tracks() == null) return 0;
        return (int) timeline.tracks().stream()
                .filter(t -> t.type() == TimelineTrack.TrackType.VIDEO)
                .count();
    }

    private boolean hasAlphaExternalLayers(TimelineExtensions extensions) {
        if (extensions == null) return false;
        return extensions.externalRenderNodes().stream()
                .anyMatch(n -> n.intermediateFormat() != null
                        && (n.intermediateFormat().contains("4444")
                        || n.intermediateFormat().contains("png")
                        || n.intermediateFormat().contains("alpha")));
    }

    private boolean hasCrossDissolveOrComplexTransition(TimelineSpec timeline) {
        return timeline.tracks().stream()
                .flatMap(t -> t.clips() != null ? t.clips().stream() : java.util.stream.Stream.empty())
                .flatMap(c -> c.effects() != null ? c.effects().stream() : java.util.stream.Stream.empty())
                .map(TimelineClipEffect::effectKey)
                .anyMatch(k -> k != null && (k.contains("cross_dissolve") || k.contains("wipe")
                        || k.contains("slide") || k.contains("dissolve")));
    }

    private boolean hasMultitrackAudioMix(TimelineSpec timeline) {
        return timeline.tracks().stream()
                .filter(t -> t.type() == TimelineTrack.TrackType.AUDIO)
                .mapToLong(t -> t.clips() != null ? t.clips().size() : 0)
                .sum() > 1;
    }
}
