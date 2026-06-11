package com.example.platform.render.infrastructure;

import java.util.List;

/**
 * Render plan containing steps, selected providers, and fallback plan.
 */
public record RenderPlan(
        String jobId,
        List<RenderStep> steps,
        List<String> selectedProviders,
        List<String> requiredCapabilities,
        RenderPlan fallbackPlan,
        String ruleVersion,
        double estimatedCost,
        long estimatedDurationMs
) {}
