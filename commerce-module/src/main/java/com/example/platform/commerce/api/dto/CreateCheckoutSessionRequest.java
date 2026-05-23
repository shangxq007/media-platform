package com.example.platform.commerce.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateCheckoutSessionRequest(
        @NotBlank String tenantId,
        @NotBlank String productCode,
        String userId,
        String purchaseMode,
        String successUrl,
        String cancelUrl
) {}
