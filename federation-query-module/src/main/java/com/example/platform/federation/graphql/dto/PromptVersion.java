package com.example.platform.federation.graphql.dto;

public record PromptVersion(
        String version,
        String createdAt,
        String createdBy,
        String changelog
) {}
