package com.example.platform.ai.api.dto;

import jakarta.validation.constraints.NotBlank;

public record GenerateScriptRequestDto(
        @NotBlank String prompt,
        String profile) {

    public String profileOrDefault() {
        return (profile == null || profile.isBlank()) ? "default_1080p" : profile;
    }
}
