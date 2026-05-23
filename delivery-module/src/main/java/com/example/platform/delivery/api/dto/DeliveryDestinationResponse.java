package com.example.platform.delivery.api.dto;

public record DeliveryDestinationResponse(
        String id,
        String tenantId,
        String name,
        String protocol,
        boolean enabled,
        String credentialRef,
        boolean credentialsConfigured) {}
