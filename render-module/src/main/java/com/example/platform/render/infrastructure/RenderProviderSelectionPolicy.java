package com.example.platform.render.infrastructure;

import com.example.platform.render.infrastructure.effects.EffectProviderRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Policy for selecting the best render provider based on profile, effects, and health.
 *
 * <p>Selection order:</p>
 * <ol>
 *   <li>Filter by profile support</li>
 *   <li>Filter by required effects</li>
 *   <li>Filter by health status</li>
 *   <li>Prefer stable over experimental</li>
 *   <li>Prefer lower latency</li>
 * </ol>
 */
@Component
public class RenderProviderSelectionPolicy {
    private static final Logger log = LoggerFactory.getLogger(RenderProviderSelectionPolicy.class);

    private final RenderProviderRegistry registry;
    private final EffectProviderRouter effectProviderRouter;

    public RenderProviderSelectionPolicy(RenderProviderRegistry registry,
                                         EffectProviderRouter effectProviderRouter) {
        this.registry = registry;
        this.effectProviderRouter = effectProviderRouter;
    }

    public Optional<RenderProvider> select(String profile, List<String> effectKeys) {
        List<RenderProviderCapability> candidates = registry.getCapabilitiesForProfile(profile);

        if (candidates.isEmpty()) {
            log.warn("No provider found for profile '{}'", profile);
            return Optional.empty();
        }

        // Filter by effect support
        List<RenderProviderCapability> effectMatches = candidates.stream()
                .filter(cap -> effectKeys == null || effectKeys.isEmpty() ||
                        effectKeys.stream().allMatch(cap::supportsEffect))
                .toList();

        if (effectMatches.isEmpty()) {
            log.warn("No provider supports all effects for profile '{}', falling back to any", profile);
            effectMatches = candidates;
        }

        // Filter by health
        List<RenderProviderCapability> healthy = effectMatches.stream()
                .filter(cap -> {
                    RenderProviderHealthCheck health = registry.getHealthCheck(cap.providerKey());
                    return health == null || health.healthy();
                })
                .toList();

        if (healthy.isEmpty()) {
            log.warn("All providers unhealthy for profile '{}', using fallback", profile);
            healthy = effectMatches;
        }

        Set<String> preferredProviders = preferredProvidersForEffects(effectKeys);

        // Prefer stable, effect-tier routing, then resolution capability
        return healthy.stream()
                .sorted(Comparator
                        .comparingInt((RenderProviderCapability c) ->
                                providerPreferenceRank(c.providerKey(), preferredProviders))
                        .thenComparing(RenderProviderCapability::experimental)
                        .thenComparingInt(c -> resolutionArea(c.maxResolution())))
                .map(cap -> registry.getProvider(cap.providerKey()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    private Set<String> preferredProvidersForEffects(List<String> effectKeys) {
        Set<String> ordered = new LinkedHashSet<>();
        if (effectKeys != null) {
            for (String key : effectKeys) {
                effectProviderRouter.resolveProviderForEffect(key, null).ifPresent(ordered::add);
            }
        }
        return ordered;
    }

    private static int providerPreferenceRank(String providerKey, Set<String> preferred) {
        if (preferred == null || preferred.isEmpty()) {
            return 0;
        }
        int idx = 0;
        for (String p : preferred) {
            if (p.equals(providerKey)) {
                return idx;
            }
            idx++;
        }
        return preferred.size() + 1;
    }

    private int resolutionArea(String resolution) {
        String[] parts = resolution.split("x");
        if (parts.length == 2) {
            return Integer.parseInt(parts[0]) * Integer.parseInt(parts[1]);
        }
        return 0;
    }
}
