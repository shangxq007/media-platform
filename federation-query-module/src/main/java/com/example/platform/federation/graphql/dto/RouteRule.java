package com.example.platform.federation.graphql.dto;

public record RouteRule(
        String scene,
        int priority,
        boolean enabled
) {}
