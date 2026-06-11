package com.example.platform.render.infrastructure;

import java.util.List;

/**
 * Render step in a render plan.
 */
public record RenderStep(
        String id,
        ProviderType providerType,
        String providerName,
        List<String> requiredCapabilities,
        String inputUri,
        String outputUri,
        List<String> dependsOn,
        boolean allowFallback,
        List<String> fallbackProviders
) {}
