package com.example.platform.config.firstrender;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * First Render plan HTTP request body.
 *
 * <p>Carries only the render target and the explicit output intent: the target revision, the real
 * render extent, the output format, and a mandatory profile. {@code tenantId} and the render
 * initiator are NOT accepted from the body — they are resolved from the authenticated security
 * context. No value here carries a default.
 */
public record FirstRenderPlanHttpRequest(
        @NotBlank String revisionId,
        @NotNull @Valid FirstRenderExtentHttpRequest extent,
        @NotNull OutputFormat outputFormat,
        @NotBlank String profile) {
}
