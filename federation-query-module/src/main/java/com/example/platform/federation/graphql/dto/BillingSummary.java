package com.example.platform.federation.graphql.dto;

public record BillingSummary(
        String currentPlan,
        MoneyDto creditBalance,
        UsageSummary usageThisMonth
) {}
