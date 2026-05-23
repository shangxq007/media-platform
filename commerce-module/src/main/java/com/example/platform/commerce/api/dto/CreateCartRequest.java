package com.example.platform.commerce.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateCartRequest(
        @NotBlank String tenantId,
        String userId) {
}
