package com.example.platform.config.render;

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
public record RenderJobSubmissionHttpRequest(
        @NotBlank String revisionId,
        @NotNull @Valid RenderJobExtentHttpRequest extent,
        @NotNull OutputFormat outputFormat,
        @NotBlank String profile) {
}
