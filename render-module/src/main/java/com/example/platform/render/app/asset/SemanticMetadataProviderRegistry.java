package com.example.platform.render.app.asset;

import com.example.platform.render.domain.asset.semantic.SemanticCapability;
import com.example.platform.render.domain.asset.semantic.SemanticMetadataProvider;
import com.example.platform.render.domain.asset.semantic.SemanticMetadataRequest;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Registry for semantic metadata providers.
 *
 * <p>Providers register via Spring. Supports capability-based lookup
 * (PROBE, ASR, OCR, VISION, EMBEDDING) in addition to request-based resolution.</p>
 */
@Component
public class SemanticMetadataProviderRegistry {

    private final Map<String, SemanticMetadataProvider> providers = new LinkedHashMap<>();

    public void register(SemanticMetadataProvider provider) {
        providers.put(provider.providerName(), provider);
    }

    public Optional<SemanticMetadataProvider> resolve(SemanticMetadataRequest request) {
        return providers.values().stream()
                .filter(p -> p.supports(request))
                .findFirst();
    }

    /**
     * Find all providers matching a specific capability.
     */
    public List<SemanticMetadataProvider> resolveByCapability(SemanticCapability capability) {
        return providers.values().stream()
                .filter(p -> p.capability() == capability)
                .toList();
    }

    /**
     * Find first provider matching a capability.
     */
    public Optional<SemanticMetadataProvider> findFirst(SemanticCapability capability) {
        return providers.values().stream()
                .filter(p -> p.capability() == capability)
                .findFirst();
    }

    public List<String> listProviders() {
        return List.copyOf(providers.keySet());
    }

    public int providerCount() {
        return providers.size();
    }
}
