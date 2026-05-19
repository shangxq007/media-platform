package com.example.platform.federation.graphql.dto;

public record RenderStats(
        int submitted,
        int completed,
        int failed,
        Double avgDurationSeconds
) {}
