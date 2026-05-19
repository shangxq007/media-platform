package com.example.platform.identity.api.dto;

import jakarta.validation.constraints.NotBlank;

public record AssignRoleRequest(
        @NotBlank String roleKey,
        String assignedBy) {}
