package com.example.platform.commerce.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateCheckoutSessionRequest(
        @NotBlank String tenantId,
        @NotBlank String productCode,
        String purchaseMode,
        String successUrl,
        String cancelUrl
) {}
