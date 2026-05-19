package com.example.platform.federation.graphql.dto;

public record ProviderHealth(
        String providerKey,
        String status,
        Integer latencyMs,
        Double errorRate
) {}
