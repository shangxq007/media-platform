package com.example.platform.render.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Routes render jobs to the appropriate provider using capability-based selection.
 *
 * <p>Delegates to {@link RenderProviderSelectionPolicy} for primary selection
 * and {@link RenderProviderFallbackPolicy} for fallback handling.</p>
 */
@Component
public class RenderProviderRouter {
    private static final Logger log = LoggerFactory.getLogger(RenderProviderRouter.class);

    private final RenderProviderFallbackPolicy fallbackPolicy;

    public RenderProviderRouter(RenderProviderFallbackPolicy fallbackPolicy) {
        this.fallbackPolicy = fallbackPolicy;
        log.info("RenderProviderRouter: initialized");
    }

    /**
     * Route a render job to the best provider for the given profile.
     */
    public RenderProvider route(String profile) {
        return fallbackPolicy.resolve(profile, List.of());
    }

    /**
     * Route a render job considering required effects.
     */
    public RenderProvider route(String profile, List<String> effectKeys) {
        return fallbackPolicy.resolve(profile, effectKeys);
    }
}
