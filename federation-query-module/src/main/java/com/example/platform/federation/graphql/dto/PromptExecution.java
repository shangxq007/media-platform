package com.example.platform.federation.graphql.dto;

public record PromptExecution(
        String executionId,
        String status,
        String riskLevel,
        MoneyDto costEstimate,
        String startedAt,
        String finishedAt
) {}
