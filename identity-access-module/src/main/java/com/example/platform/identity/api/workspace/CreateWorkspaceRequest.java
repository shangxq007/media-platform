package com.example.platform.identity.api.workspace;

import jakarta.validation.constraints.NotBlank;

public record CreateWorkspaceRequest(
        @NotBlank String name,
        String description,
        String planTier) {}
