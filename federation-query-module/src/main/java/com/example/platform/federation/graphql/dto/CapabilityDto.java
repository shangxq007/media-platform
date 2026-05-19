package com.example.platform.federation.graphql.dto;

public record CapabilityDto(
        String featureKey,
        boolean allowed,
        String reasonCode,
        Double quotaRemaining,
        String expiresAt,
        String source
) {}
