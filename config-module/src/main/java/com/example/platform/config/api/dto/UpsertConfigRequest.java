package com.example.platform.config.api.dto;

import jakarta.validation.constraints.NotBlank;

public record UpsertConfigRequest(@NotBlank String namespaceKey, @NotBlank String configKey, @NotBlank String valueJson) {}