package com.example.platform.identity.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateWorkspaceGroupRequest(
        @NotBlank String name,
        String description) {}
