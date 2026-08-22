package com.example.platform.render.infrastructure;

import com.example.platform.render.domain.renderplan.RenderExtent;
import java.util.List;

/**
 * Render job definition.
 *
 * <p>PRE-#21 C10/C11: {@code requestedExtent} carries the typed requested
 * render extent (exact half-open [start,end) + rational frame rate) through
 * the real orchestration path. Null when the job is not an extent-governed
 * render (then the result is ordinary success, never authoritative
 * extent-proven success).
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
        List<String> blockedProviders,
        RenderExtent requestedExtent
) {}
