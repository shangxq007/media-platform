package com.example.platform.render.infrastructure;

import java.util.List;

/**
 * Render plan containing steps, selected providers, and fallback plan.
 */
public record ProviderRenderPlan(
        String jobId,
        List<RenderStep> steps,
        List<String> selectedProviders,
        List<String> requiredCapabilities,
        ProviderRenderPlan fallbackPlan,
        String ruleVersion,
        double estimatedCost,
        long estimatedDurationMs
) {}
