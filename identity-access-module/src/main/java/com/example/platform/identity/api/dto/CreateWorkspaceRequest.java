package com.example.platform.identity.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateWorkspaceRequest(
        @NotBlank String name,
        String description,
        String planTier) {}
