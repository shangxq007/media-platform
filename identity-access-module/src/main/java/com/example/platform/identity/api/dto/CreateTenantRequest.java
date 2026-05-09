package com.example.platform.identity.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateTenantRequest(@NotBlank String name) {}
