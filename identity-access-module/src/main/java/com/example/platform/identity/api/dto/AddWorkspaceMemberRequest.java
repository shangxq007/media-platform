package com.example.platform.identity.api.dto;

import jakarta.validation.constraints.NotBlank;

public record AddWorkspaceMemberRequest(
        @NotBlank String userId,
        @NotBlank String role) {}
