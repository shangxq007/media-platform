package com.example.platform.render.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Request to render a TimelineRevision into a final render Product.
 *
 * <p>This is a minimal request that accepts only the output profile.
 * The caller does not choose internal provider/backend/environment/storage provider.</p>
 *
 * @param outputProfile the render profile (e.g., "default_1080p", "default_720p")
 */
@Schema(description = "渲染 TimelineRevision 请求")
public record TimelineRevisionRenderRequest(
        @NotBlank @Schema(description = "渲染配置", example = "default_1080p") String outputProfile) {

    /**
     * Returns the profile value or a default if blank.
     */
    public String profileOrDefault() {
        return (outputProfile == null || outputProfile.isBlank()) ? "default_1080p" : outputProfile;
    }
}
