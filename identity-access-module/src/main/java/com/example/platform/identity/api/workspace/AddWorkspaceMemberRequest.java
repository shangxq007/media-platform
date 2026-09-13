package com.example.platform.identity.api.workspace;

import jakarta.validation.constraints.NotBlank;

public record AddWorkspaceMemberRequest(
        @NotBlank String userId,
        @NotBlank String role) {}
