package com.example.platform.render.app.dto;

import jakarta.validation.constraints.NotBlank;

public record SubmitRenderJobRequest(
        @NotBlank String tenantId,
        @NotBlank String projectId,
        @NotBlank String prompt,
        String profile) {

    public String profileOrDefault() {
        return (profile == null || profile.isBlank()) ? "default_1080p" : profile;
    }
}
