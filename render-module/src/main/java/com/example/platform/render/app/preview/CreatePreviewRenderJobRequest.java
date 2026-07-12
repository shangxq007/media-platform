package com.example.platform.render.app.preview;

import jakarta.validation.constraints.NotBlank;

/**
 * Request to create a new preview render job.
 *
 * <p>Validates required fields at the API boundary. Profile defaults
 * to "default_1080p" if not specified.</p>
 *
 * @param tenantId      the tenant identifier (must not be blank)
 * @param projectId     the project identifier (must not be blank)
 * @param snapshotId    the timeline snapshot identifier (must not be blank)
 * @param profile       the render profile (nullable, defaults to "default_1080p")
 */
public record CreatePreviewRenderJobRequest(
        @NotBlank String tenantId,
        @NotBlank String projectId,
        @NotBlank String snapshotId,
        String profile
) {

    /**
     * Returns the profile value or a default if blank.
     *
     * @return the render profile, never null or blank
     */
    public String profileOrDefault() {
        return (profile == null || profile.isBlank()) ? "default_1080p" : profile;
    }
}
