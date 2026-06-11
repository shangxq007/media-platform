package com.example.platform.render.infrastructure;

import java.util.List;

/**
 * Render job definition.
 */
public record RenderJob(
        String id,
        String jobType,
        String mode,
        String canvas,
        List<String> assets,
        String timeline,
        String captions,
        String style,
        String output,
        List<String> requiredCapabilities,
        RenderConstraints constraints,
        boolean allowDegrade,
        List<String> preferredProviders,
        List<String> blockedProviders
) {}


