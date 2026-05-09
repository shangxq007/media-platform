package com.example.platform.render.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request to submit a render job for execution.
 *
 * <p>This DTO is part of the render module's public API surface and can be
 * used by other modules to submit render jobs through the
 * {@link com.example.platform.render.api.port.RenderOrchestratorPort}.</p>
 *
 * @param tenantId  the tenant identifier (must not be blank)
 * @param projectId the project identifier (must not be blank)
 * @param prompt    the AI prompt/script for the render (must not be blank)
 * @param profile   the render profile (e.g., "default_1080p", "4k"); defaults to "default_1080p" if blank
 */
public record SubmitRenderJobRequest(
        @NotBlank String tenantId,
        @NotBlank String projectId,
        @NotBlank String prompt,
        String profile) {

    /**
     * Returns the profile value or a default if blank.
     *
     * @return the render profile, never null or blank
     */
    public String profileOrDefault() {
        return (profile == null || profile.isBlank()) ? "default_1080p" : profile;
    }
}
