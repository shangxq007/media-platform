package com.example.platform.identity.api.dto;

public record CreateApiKeyResponse(
        String id,
        String apiKey,
        String fingerprint,
        String principal,
        java.time.Instant createdAt) {}
