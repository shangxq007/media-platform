package com.example.platform.render.infrastructure;

import com.example.platform.render.domain.timeline.TimelineExtensions;
import com.example.platform.render.domain.timeline.TimelineSpec;
import com.example.platform.render.domain.timeline.TimelineExtensionsReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Routes render jobs to the appropriate provider using unified resolution.
 *
 * <p>Combines timeline-aware composer selection with health-aware fallback
 * via {@link RenderProviderResolver}.</p>
 */
@Component
public class RenderProviderRouter {
    private static final Logger log = LoggerFactory.getLogger(RenderProviderRouter.class);

    private final RenderProviderResolver resolver;
    private final RenderProviderFallbackPolicy fallbackPolicy;
    private final TimelineExtensionsReader extensionsReader;

    public RenderProviderRouter(RenderProviderResolver resolver,
                                 RenderProviderFallbackPolicy fallbackPolicy,
                                 TimelineExtensionsReader extensionsReader) {
        this.resolver = resolver;
        this.fallbackPolicy = fallbackPolicy;
        this.extensionsReader = extensionsReader;
        log.info("RenderProviderRouter: initialized with unified resolver");
    }

    /**
     * Route a render job to the best provider for the given profile.
     * Uses simple fallback when no timeline context is available.
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

    /**
     * Route with full timeline context — uses unified resolver for
     * timeline-aware selection with health-aware degradation.
     */
    public RenderProviderResolver.ResolvedProvider routeWithContext(
            TimelineSpec timeline, String profile, List<String> effectKeys) {
        TimelineExtensions extensions = timeline != null
                ? extensionsReader.fromSpec(timeline) : null;
        return resolver.resolve(timeline, extensions, profile, effectKeys);
    }
}
