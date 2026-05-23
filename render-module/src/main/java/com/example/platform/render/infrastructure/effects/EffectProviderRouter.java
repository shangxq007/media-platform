package com.example.platform.render.infrastructure.effects;

import com.example.platform.render.infrastructure.EffectDescriptor;
import com.example.platform.render.infrastructure.EffectMappingService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Selects render provider tier for effects: lightweight (javacv/ffmpeg) before OFX/GPU.
 */
@Component
public class EffectProviderRouter {

    private static final Logger log = LoggerFactory.getLogger(EffectProviderRouter.class);

    /** Providers tried in order when multiple mappings exist. */
    private static final List<String> PROVIDER_PRIORITY = List.of(
            "ffmpeg", "javacv", "gstreamer", "mlt", "natron", "ofx", "gpac", "bento4", "shotstack");

    private final EffectMappingService effectMapping;

    public EffectProviderRouter(EffectMappingService effectMapping) {
        this.effectMapping = effectMapping;
    }

    /**
     * Resolves the best provider key for a single effect given tenant-allowed providers.
     */
    public Optional<String> resolveProviderForEffect(String effectKey, Set<String> allowedProviders) {
        Optional<EffectDescriptor> descriptor = effectMapping.getDescriptor(effectKey);
        if (descriptor.isEmpty()) {
            log.warn("Unknown effect key: {}", effectKey);
            return Optional.empty();
        }
        List<String> mappings = descriptor.get().providerKeys();
        for (String preferred : PROVIDER_PRIORITY) {
            if (mappings.contains(preferred)
                    && (allowedProviders == null || allowedProviders.isEmpty()
                    || allowedProviders.contains(preferred))) {
                return Optional.of(preferred);
            }
        }
        return mappings.stream()
                .filter(p -> allowedProviders == null || allowedProviders.isEmpty() || allowedProviders.contains(p))
                .findFirst();
    }

    /**
     * Collects distinct provider keys required to render all effects (for pipeline routing).
     */
    public List<String> resolveProvidersForEffects(List<String> effectKeys, Set<String> allowedProviders) {
        Set<String> ordered = new LinkedHashSet<>();
        if (effectKeys != null) {
            for (String key : effectKeys) {
                resolveProviderForEffect(key, allowedProviders).ifPresent(ordered::add);
            }
        }
        return new ArrayList<>(ordered);
    }

    public boolean requiresOfxPipeline(List<String> effectKeys, Set<String> allowedProviders) {
        return resolveProvidersForEffects(effectKeys, allowedProviders).contains("ofx");
    }

    public boolean requiresNatronPipeline(List<String> effectKeys, Set<String> allowedProviders) {
        if (effectKeys == null) {
            return false;
        }
        for (String effectKey : effectKeys) {
            Optional<EffectDescriptor> descriptor = effectMapping.getDescriptor(effectKey);
            if (descriptor.isEmpty()) {
                continue;
            }
            boolean natronMapped = descriptor.get().providerKeys().contains("natron");
            if (!natronMapped) {
                continue;
            }
            if (allowedProviders == null || allowedProviders.isEmpty()
                    || allowedProviders.contains("natron")) {
                return true;
            }
        }
        return false;
    }
}
