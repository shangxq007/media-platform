package com.example.platform.federation.graphql.dto;

public record ExtensionSummary(
        int installed,
        int enabled,
        int highRisk,
        int sandboxJobsRunning
) {}
