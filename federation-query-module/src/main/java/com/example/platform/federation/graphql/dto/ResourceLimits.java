package com.example.platform.federation.graphql.dto;

public record ResourceLimits(
        int timeoutMs,
        int maxConcurrency,
        int maxOutputBytes
) {}
