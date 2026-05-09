package com.example.platform.ai.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatRequestDto(@NotBlank String capability, @NotBlank String prompt) {}